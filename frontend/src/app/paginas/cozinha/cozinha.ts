import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { AuthService } from '../../servicos/auth.service';
import { DestinoPreparo, NOMES_DOS_DESTINOS } from '../../servicos/cardapio.service';
import { ComandasService, Pedido } from '../../servicos/comandas.service';
import { mensagemDoErro } from '../../servicos/erros';
import { TempoRealService } from '../../servicos/tempo-real.service';

type Filtro = 'COZINHA' | 'BAR' | 'TODOS';
type Urgencia = 'ok' | 'atencao' | 'atrasado';

const CHAVE_SOM = 'reston.cozinha.som';

@Component({
  selector: 'app-cozinha',
  templateUrl: './cozinha.html',
  styleUrl: './cozinha.css',
})
export class Cozinha implements OnInit {
  private api = inject(ComandasService);
  private auth = inject(AuthService);
  private tempoReal = inject(TempoRealService);
  private destroyRef = inject(DestroyRef);

  filtros: Filtro[] = ['COZINHA', 'BAR', 'TODOS'];
  nomesDosDestinos = NOMES_DOS_DESTINOS;

  pedidos = signal<Pedido[]>([]);
  carregando = signal(true);
  erro = signal('');
  filtro = signal<Filtro>('COZINHA');
  agora = signal(Date.now());
  trabalhando = signal<number | null>(null); // pedido que está mudando de status
  som = signal(true);

  // O bartender só vê o bar: a API já filtra, a tela só acompanha
  modoBar = computed(() => this.auth.usuario()?.perfil === 'BARTENDER');

  private audio: AudioContext | null = null;
  private idsAnteriores: Set<number> | null = null; // null até a primeira carga

  // Produtos sem preparo nunca passam pela cozinha: o garçom entrega direto
  doPreparo = computed(() => this.pedidos().filter((p) => p.destino !== 'SEM_PREPARO'));

  visiveis = computed(() => {
    const filtro = this.filtro();
    return this.doPreparo().filter((p) => filtro === 'TODOS' || p.destino === filtro);
  });

  contagem = computed(() => ({
    COZINHA: this.doPreparo().filter((p) => p.destino === 'COZINHA').length,
    BAR: this.doPreparo().filter((p) => p.destino === 'BAR').length,
    TODOS: this.doPreparo().length,
  }));

  ngOnInit(): void {
    this.som.set(this.lerSom());
    if (this.modoBar()) this.filtro.set('BAR');
    this.carregar();
    this.tempoReal.ouvir('pedidos').pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.carregar(true));

    // Só para os minutos na tela andarem; os pedidos chegam pelo tempo real
    const relogio = setInterval(() => this.agora.set(Date.now()), 15000);
    this.destroyRef.onDestroy(() => clearInterval(relogio));
  }

  carregar(silencioso = false): void {
    if (!silencioso) this.carregando.set(true);
    this.api.pedidosEmAndamento().subscribe({
      next: (lista) => {
        this.avisarSeTemPedidoNovo(lista);
        this.pedidos.set(lista);
        if (!silencioso) this.erro.set('');
        this.carregando.set(false);
      },
      error: (erro: HttpErrorResponse) => {
        if (!silencioso) this.erro.set(mensagemDoErro(erro));
        this.carregando.set(false);
      },
    });
  }

  private avisarSeTemPedidoNovo(lista: Pedido[]): void {
    const anteriores = this.idsAnteriores;
    this.idsAnteriores = new Set(lista.map((p) => p.id));
    if (!anteriores || !this.som()) return;

    const filtro = this.filtro();
    const temNovo = lista.some(
      (p) =>
        p.status === 'RECEBIDO' &&
        p.destino !== 'SEM_PREPARO' &&
        (filtro === 'TODOS' || p.destino === filtro) &&
        !anteriores.has(p.id),
    );
    if (temNovo) this.tocarPlim();
  }

  // O navegador só libera o áudio depois de um gesto do usuário
  @HostListener('document:click')
  liberarAudio(): void {
    try {
      this.audio ??= new AudioContext();
      if (this.audio.state === 'suspended') void this.audio.resume();
    } catch {
      this.audio = null;
    }
  }

  tocarPlim(): void {
    const ctx = this.audio;
    if (!ctx || ctx.state !== 'running') return; // ainda bloqueado: fica em silêncio

    try {
      const inicio = ctx.currentTime;
      [880, 1320].forEach((frequencia, i) => {
        const osc = ctx.createOscillator();
        const volume = ctx.createGain();
        const comeca = inicio + i * 0.15;
        osc.frequency.value = frequencia;
        volume.gain.setValueAtTime(0.0001, comeca);
        volume.gain.exponentialRampToValueAtTime(0.25, comeca + 0.02);
        volume.gain.exponentialRampToValueAtTime(0.0001, comeca + 0.15);
        osc.connect(volume).connect(ctx.destination);
        osc.start(comeca);
        osc.stop(comeca + 0.15);
      });
    } catch {
      // sem som a tela continua funcionando
    }
  }

  alternarSom(): void {
    this.som.update((ligado) => !ligado);
    try {
      localStorage.setItem(CHAVE_SOM, this.som() ? '1' : '0');
    } catch {
      // armazenamento bloqueado: a escolha vale só até fechar a página
    }
  }

  private lerSom(): boolean {
    try {
      return localStorage.getItem(CHAVE_SOM) !== '0';
    } catch {
      return true;
    }
  }

  // A cozinha não avisa que começou: o pedido fica na fila até alguém marcar como pronto.
  fila = computed(() =>
    this.visiveis()
      .filter((p) => p.status !== 'PRONTO')
      .sort((x, y) => new Date(x.criadoEm).getTime() - new Date(y.criadoEm).getTime()),
  );

  prontos = computed(() => this.visiveis().filter((p) => p.status === 'PRONTO'));

  marcarPronto(pedido: Pedido): void {
    this.trabalhando.set(pedido.id);
    this.api.mudarStatus(pedido.id, 'PRONTO').subscribe({
      next: () => {
        this.trabalhando.set(null);
        this.carregar(true);
      },
      error: (erro: HttpErrorResponse) => {
        this.trabalhando.set(null);
        this.erro.set(mensagemDoErro(erro));
        this.carregar(true);
      },
    });
  }

  // Minutos desde que o pedido chegou
  minutos(pedido: Pedido): number {
    return Math.max(0, Math.floor((this.agora() - new Date(pedido.criadoEm).getTime()) / 60000));
  }

  // Quanto do tempo previsto já passou (0 a 100; passa de 100 quando atrasa)
  andamento(pedido: Pedido): number {
    if (pedido.tempoPrevistoMin <= 0) return 0;
    return Math.round((this.minutos(pedido) / pedido.tempoPrevistoMin) * 100);
  }

  // 45 vira "45 min"; 220 vira "3 h 40"
  tempo(minutos: number): string {
    if (minutos < 60) return minutos + ' min';
    const resto = minutos % 60;
    return Math.floor(minutos / 60) + ' h' + (resto ? ' ' + String(resto).padStart(2, '0') : '');
  }

  minutosDeAtraso(pedido: Pedido): number {
    return Math.max(1, this.minutos(pedido) - pedido.tempoPrevistoMin);
  }

  ehNovo(pedido: Pedido): boolean {
    return this.minutos(pedido) < 1;
  }

  urgencia(pedido: Pedido): Urgencia {
    if (pedido.status === 'PRONTO' || pedido.tempoPrevistoMin <= 0) return 'ok';
    const andamento = this.andamento(pedido);
    return andamento > 100 ? 'atrasado' : andamento >= 75 ? 'atencao' : 'ok';
  }

  quantos(filtro: Filtro): number {
    return this.contagem()[filtro];
  }

  escolherFiltro(filtro: Filtro): void {
    this.filtro.set(filtro);
  }

  destinoDoFiltro(filtro: Filtro): string {
    return filtro === 'TODOS' ? 'Todos' : this.nomesDosDestinos[filtro as DestinoPreparo];
  }
}
