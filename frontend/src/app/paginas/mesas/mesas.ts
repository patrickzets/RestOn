import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, DestroyRef, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, forkJoin, of, switchMap } from 'rxjs';

import { AuthService } from '../../servicos/auth.service';
import { Comanda, ComandasService, Pedido } from '../../servicos/comandas.service';
import { mensagemDoErro } from '../../servicos/erros';
import { Mesa, MesasService } from '../../servicos/mesas.service';
import { ReservasService } from '../../servicos/reservas.service';
import { TempoRealService } from '../../servicos/tempo-real.service';

type Filtro = 'TODAS' | 'LIVRE' | 'OCUPADA' | 'PRONTO' | 'MINHAS';
type Confirmacao = 'nova-comanda' | 'cancelar-comanda' | 'encerrar' | null;

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

@Component({
  selector: 'app-mesas',
  imports: [DatePipe, FormsModule, ReactiveFormsModule, RouterLink],
  templateUrl: './mesas.html',
  styleUrl: './mesas.css',
})
export class Mesas implements OnInit {
  private api = inject(MesasService);
  private reservasApi = inject(ReservasService);
  private comandasApi = inject(ComandasService);
  private tempoReal = inject(TempoRealService);
  protected auth = inject(AuthService);

  // Quem abriu a janela: o foco volta para lá quando ela fecha
  private origemDaMesa: HTMLElement | null = null;
  private origemDoFormulario: HTMLElement | null = null;
  private fb = inject(NonNullableFormBuilder);
  private destroyRef = inject(DestroyRef);

  mesas = signal<Mesa[]>([]);
  carregando = signal(true);
  erroDaLista = signal('');
  filtro = signal<Filtro>('TODAS');
  setorEscolhido = signal('TODOS');
  numeroDigitado = signal('');
  erroDoNumero = signal('');
  agora = signal(Date.now());

  // Janela da mesa (comanda por cima da lista)
  mesaAbertaId = signal<number | null>(null);
  comandasDaMesa = signal<Comanda[]>([]);
  comandaEscolhidaId = signal<number | null>(null);
  comandaAtual = signal<Comanda | null>(null);
  prontosParaLevar = signal<number[]>([]); // pedidos que estavam prontos quando o garçom abriu a mesa
  confirmacao = signal<Confirmacao>(null);
  pedidoCancelando = signal<number | null>(null);
  motivo = signal('');
  maisAcoes = signal(false);
  destinoDaTroca = signal<number | null>(null);
  salvando = signal(false);
  erroDoPainel = signal('');

  // Janela de cadastro da mesa (só gerente)
  formulario = signal<'fechado' | 'nova' | 'editar'>('fechado');
  form = this.fb.group({
    numero: [1, [Validators.required, Validators.min(1), Validators.max(999)]],
    capacidade: [4, [Validators.required, Validators.min(1), Validators.max(30)]],
    setor: ['Salão', [Validators.required, Validators.maxLength(30)]],
    ativa: [true],
  });

  ehGestor = computed(() => ['ADMIN', 'GERENTE'].includes(this.auth.usuario()?.perfil ?? ''));
  podeOperar = computed(() => ['ADMIN', 'GERENTE', 'GARCOM'].includes(this.auth.usuario()?.perfil ?? ''));
  selecionada = computed(() => this.mesas().find((m) => m.id === this.mesaAbertaId()) ?? null);

  // O garçom não precisa ver as mesas desativadas
  doSalao = computed(() => this.mesas().filter((m) => this.ehGestor() || m.ativa));

  setores = computed(() => [...new Set(this.doSalao().map((m) => m.setor))]);

  visiveis = computed(() => {
    const filtro = this.filtro();
    const setor = this.setorEscolhido();
    return this.doSalao().filter((m) => (setor === 'TODOS' || m.setor === setor) && this.passaNoFiltro(m, filtro));
  });

  // Um bloco por setor, com a contagem para o título
  grupos = computed(() =>
    this.setores()
      .map((nome) => {
        const doSetor = this.visiveis().filter((m) => m.setor === nome);
        return {
          nome,
          mesas: doSetor,
          ocupadas: doSetor.filter((m) => m.estado === 'OCUPADA').length,
          prontas: doSetor.filter((m) => m.pedidosProntos > 0).length,
        };
      })
      .filter((g) => g.mesas.length > 0),
  );

  contagem = computed(() => {
    const ativas = this.mesas().filter((m) => m.ativa);
    return {
      todas: ativas.length,
      livres: ativas.filter((m) => this.passaNoFiltro(m, 'LIVRE')).length,
      ocupadas: ativas.filter((m) => this.passaNoFiltro(m, 'OCUPADA')).length,
      prontas: ativas.filter((m) => this.passaNoFiltro(m, 'PRONTO')).length,
      minhas: ativas.filter((m) => this.passaNoFiltro(m, 'MINHAS')).length,
    };
  });

  comandasAbertas = computed(() => this.comandasDaMesa().filter((c) => c.status === 'ABERTA'));

  // Para onde dá para levar quem está sentado: mesas livres e sem reserva chegando
  mesasParaTrocar = computed(() =>
    this.mesas().filter((m) => m.ativa && m.estado === 'LIVRE' && !m.reservaProxima && m.id !== this.mesaAbertaId()),
  );

  // Pedidos da comanda aberta na janela: o que está pronto vem primeiro
  pedidosParaLevar = computed(() => (this.comandaAtual()?.pedidos ?? []).filter((p) => this.paraLevar(p)));
  pedidosEmPreparo = computed(() => (this.comandaAtual()?.pedidos ?? []).filter((p) => p.status === 'RECEBIDO' || p.status === 'EM_PREPARO'));
  pedidosAnteriores = computed(() => (this.comandaAtual()?.pedidos ?? []).filter((p) => !this.paraLevar(p) && !this.pedidosEmPreparo().includes(p)));

  ngOnInit(): void {
    this.carregar();
    // O servidor avisa quando algo muda (mesa, comanda, pedido pronto); aqui só recarregamos
    this.tempoReal.ouvir('mesas').pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.carregar(true));

    // Só para o "sentados há X min" andar
    const relogio = setInterval(() => this.agora.set(Date.now()), 30000);
    this.destroyRef.onDestroy(() => clearInterval(relogio));
  }

  carregar(silencioso = false): void {
    if (!silencioso) this.carregando.set(true);
    this.api.listar().subscribe({
      next: (lista) => {
        this.mesas.set(lista);
        this.erroDaLista.set('');
        this.carregando.set(false);
        if (this.selecionada()) this.carregarDetalhes(false);
      },
      error: (erro: HttpErrorResponse) => {
        if (!silencioso) this.erroDaLista.set(mensagemDoErro(erro));
        this.carregando.set(false);
      },
    });
  }

  // ---- lista ----

  private passaNoFiltro(mesa: Mesa, filtro: Filtro): boolean {
    switch (filtro) {
      case 'LIVRE':
        return mesa.ativa && mesa.estado === 'LIVRE';
      case 'OCUPADA':
        return mesa.estado === 'OCUPADA';
      case 'PRONTO':
        return mesa.pedidosProntos > 0;
      case 'MINHAS':
        return mesa.estado === 'OCUPADA' && mesa.abertoPor === this.auth.usuario()?.id;
      default:
        return true;
    }
  }

  escolherFiltro(filtro: Filtro): void {
    this.filtro.set(filtro);
  }

  escolherSetor(setor: string): void {
    this.setorEscolhido.set(setor);
  }

  // O garçom digita o número da mesa e aperta Enter
  irParaMesa(): void {
    const numero = Number(this.numeroDigitado());
    const mesa = this.doSalao().find((m) => m.numero === numero);
    if (!mesa) {
      this.erroDoNumero.set(`Não achei a mesa ${this.numeroDigitado()}.`);
      return;
    }
    this.erroDoNumero.set('');
    this.numeroDigitado.set('');
    this.abrirMesa(mesa);
  }

  nomeDoEstado(mesa: Mesa): string {
    if (mesa.pedidosProntos > 0) return 'pedido pronto para levar';
    if (mesa.estado === 'OCUPADA') return 'ocupada';
    if (mesa.estado === 'AGUARDANDO_LIBERACAO') return 'aguardando liberação';
    return mesa.reservaProxima ? 'reservada' : 'livre';
  }

  legenda(mesa: Mesa): string {
    if (!mesa.ativa) return 'desativada';
    if (mesa.estado === 'OCUPADA') return this.tempoDesde(mesa.atendimentoAbertoEm);
    if (mesa.estado === 'AGUARDANDO_LIBERACAO') return 'limpar';
    return '';
  }

  // ---- janela da mesa ----

  abrirMesa(mesa: Mesa): void {
    this.origemDaMesa = document.activeElement as HTMLElement | null;
    this.limparJanela();
    this.mesaAbertaId.set(mesa.id);
    this.comandasDaMesa.set([]);
    this.comandaAtual.set(null);
    this.carregarDetalhes(true);
    this.focarJanela();
  }

  fecharMesa(): void {
    this.mesaAbertaId.set(null);
    this.limparJanela();
    this.devolverFoco(this.origemDaMesa);
  }

  // Carrega as comandas da mesa e o detalhe da que está na tela.
  // Ao abrir (primeira vez), o que estava pronto passa a valer como entregue: o garçom já viu o aviso.
  private carregarDetalhes(aoAbrir: boolean): void {
    const mesa = this.selecionada();
    if (!mesa || mesa.estado !== 'OCUPADA' || !mesa.atendimentoId) {
      this.comandasDaMesa.set([]);
      this.comandaAtual.set(null);
      return;
    }

    this.comandasApi.listar({ atendimentoId: mesa.atendimentoId }).subscribe((lista) => {
      this.comandasDaMesa.set(lista);
      const escolhida = lista.find((c) => c.id === this.comandaEscolhidaId()) ?? lista.find((c) => c.status === 'ABERTA') ?? lista[0];
      this.comandaEscolhidaId.set(escolhida?.id ?? null);
      if (escolhida) this.comandasApi.detalhe(escolhida.id).subscribe((c) => this.comandaAtual.set(c));
    });

    if (aoAbrir) {
      this.comandasApi.pedidosEmAndamento().subscribe((todos) => {
        const prontos = todos.filter((p) => p.mesaNumero === mesa.numero && p.status === 'PRONTO');
        this.prontosParaLevar.set(prontos.map((p) => p.id));
        if (prontos.length > 0 && this.podeOperar()) {
          forkJoin(prontos.map((p) => this.comandasApi.mudarStatus(p.id, 'ENTREGUE'))).subscribe(() => this.carregar(true));
        }
      });
    }
  }

  escolherComanda(id: number): void {
    this.comandaEscolhidaId.set(id);
    this.confirmacao.set(null);
    this.pedidoCancelando.set(null);
    this.erroDoPainel.set('');
    this.comandasApi.detalhe(id).subscribe((c) => this.comandaAtual.set(c));
  }

  rotulo(c: Comanda): string {
    return 'Comanda ' + c.numeroCliente;
  }

  // Pedido que o garçom ainda tem que levar à mesa (estava pronto quando ele abriu a janela)
  paraLevar(pedido: Pedido): boolean {
    return pedido.status === 'PRONTO' || this.prontosParaLevar().includes(pedido.id);
  }

  semItensAtivos(comanda: Comanda): boolean {
    return (comanda.pedidos ?? []).every((p) => p.itens.every((i) => i.cancelado));
  }

  // O garçom só cancela pedido que a cozinha ainda não começou; gerente cancela até pronto
  podeCancelarPedido(pedido: Pedido): boolean {
    const emAberto = pedido.status !== 'ENTREGUE' && pedido.status !== 'CANCELADO' && !pedido.itens.every((i) => i.cancelado);
    return this.podeOperar() && this.comandaAtual()?.status === 'ABERTA' && emAberto && (this.ehGestor() || pedido.status === 'RECEBIDO');
  }

  // ---- ações da mesa ----

  abrirAtendimento(): void {
    const mesa = this.selecionada();
    if (mesa) this.executar(this.api.abrirAtendimento(mesa.id).pipe(switchMap((atendimento) => this.comandasApi.abrir(atendimento.id))));
  }

  registrarChegada(): void {
    const reserva = this.selecionada()?.reservaProxima;
    if (reserva) this.executar(this.reservasApi.registrarChegada(reserva.id));
  }

  liberar(): void {
    const mesa = this.selecionada();
    if (mesa) this.executar(this.api.liberar(mesa.id));
  }

  // As três ações abaixo só acontecem depois de uma confirmação que explica o que vai acontecer
  pedirConfirmacao(tipo: Confirmacao): void {
    this.erroDoPainel.set('');
    this.pedidoCancelando.set(null);
    this.confirmacao.set(tipo);
  }

  novaComanda(): void {
    const mesa = this.selecionada();
    if (mesa?.atendimentoId) this.executar(this.comandasApi.abrir(mesa.atendimentoId).pipe(switchMap((c) => { this.comandaEscolhidaId.set(c.id); return of(c); })));
  }

  cancelarComanda(): void {
    const comanda = this.comandaAtual();
    if (comanda) this.executar(this.comandasApi.cancelar(comanda.id));
  }

  encerrarAtendimento(): void {
    const mesa = this.selecionada();
    if (mesa?.atendimentoId) this.executar(this.api.encerrarAtendimento(mesa.atendimentoId), () => this.fecharMesa());
  }

  trocarMesa(): void {
    const mesa = this.selecionada();
    const destino = this.destinoDaTroca();
    if (mesa?.atendimentoId && destino) this.executar(this.api.trocarMesa(mesa.atendimentoId, destino), () => this.fecharMesa());
  }

  comecarACancelarPedido(pedido: Pedido): void {
    this.confirmacao.set(null);
    this.erroDoPainel.set('');
    this.motivo.set('');
    this.pedidoCancelando.set(pedido.id);
  }

  // Cancelar o pedido é cancelar todos os itens dele, com o mesmo motivo
  confirmarCancelamentoDoPedido(pedido: Pedido): void {
    if (this.motivo().trim().length < 3) {
      this.erroDoPainel.set('Informe o motivo do cancelamento (pelo menos 3 letras).');
      return;
    }
    const itens = pedido.itens.filter((i) => !i.cancelado);
    this.executar(forkJoin(itens.map((i) => this.comandasApi.cancelarItem(i.id, this.motivo()))));
  }

  // ---- cadastro da mesa (gerente) ----

  nova(): void {
    this.origemDoFormulario = document.activeElement as HTMLElement | null;
    this.limparJanela();
    const maior = this.mesas().reduce((m, mesa) => Math.max(m, mesa.numero), 0);
    this.form.reset({ numero: maior + 1, capacidade: 4, setor: this.setorEscolhido() === 'TODOS' ? 'Salão' : this.setorEscolhido(), ativa: true });
    this.formulario.set('nova');
    this.focarJanela();
  }

  editar(): void {
    const mesa = this.selecionada();
    if (!mesa) return;
    this.origemDoFormulario = document.activeElement as HTMLElement | null;
    this.erroDoPainel.set('');
    this.form.reset({ numero: mesa.numero, capacidade: mesa.capacidade, setor: mesa.setor, ativa: mesa.ativa });
    this.formulario.set('editar');
    this.focarJanela();
  }

  fecharFormulario(): void {
    this.formulario.set('fechado');
    this.erroDoPainel.set('');
    this.devolverFoco(this.origemDoFormulario);
  }

  salvarMesa(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const dados = this.form.getRawValue();
    const atual = this.selecionada();
    if (this.formulario() === 'nova') {
      this.executar(this.api.criar(dados), () => this.fecharFormulario());
    } else if (atual) {
      this.executar(this.api.atualizar(atual.id, dados), () => this.fecharFormulario());
    }
  }

  invalido(campo: 'numero' | 'capacidade' | 'setor'): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && controle.touched;
  }

  // ---- apoio ----

  preco(valor: number): string {
    return MOEDA.format(valor);
  }

  // "35 min" ou "1 h 05"
  tempoDesde(iso: string | null): string {
    if (!iso) return '';
    const minutos = Math.max(0, Math.floor((this.agora() - new Date(iso).getTime()) / 60000));
    if (minutos < 60) return `${minutos} min`;
    return `${Math.floor(minutos / 60)} h ${String(minutos % 60).padStart(2, '0')}`;
  }

  // Esc fecha a janela que estiver por cima
  @HostListener('document:keydown.escape')
  aoApertarEsc(): void {
    if (this.formulario() !== 'fechado') this.fecharFormulario();
    else if (this.mesaAbertaId() !== null) this.fecharMesa();
  }

  // Roda uma ação da API, mostra o erro na janela e atualiza a lista quando dá certo
  private executar(chamada: Observable<unknown>, depois?: () => void): void {
    this.salvando.set(true);
    this.erroDoPainel.set('');
    chamada.subscribe({
      next: () => {
        this.salvando.set(false);
        this.destinoDaTroca.set(null);
        this.confirmacao.set(null);
        this.pedidoCancelando.set(null);
        this.motivo.set('');
        depois?.();
        this.carregar(true);
      },
      error: (erro: HttpErrorResponse) => {
        this.salvando.set(false);
        this.confirmacao.set(null);
        this.erroDoPainel.set(mensagemDoErro(erro));
      },
    });
  }

  private limparJanela(): void {
    this.erroDoPainel.set('');
    this.salvando.set(false);
    this.confirmacao.set(null);
    this.pedidoCancelando.set(null);
    this.motivo.set('');
    this.maisAcoes.set(false);
    this.destinoDaTroca.set(null);
    this.prontosParaLevar.set([]);
    this.comandaEscolhidaId.set(null);
    this.formulario.set('fechado');
  }

  // O Tab fica dentro da janela: do último campo volta ao primeiro, e o Shift+Tab faz o contrário
  prenderFoco(evento: KeyboardEvent): void {
    if (evento.key !== 'Tab') return;
    const janela = evento.currentTarget as HTMLElement;
    const focaveis = Array.from(janela.querySelectorAll<HTMLElement>('a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), summary, [tabindex="0"]'));
    if (focaveis.length === 0) return;
    const primeiro = focaveis[0];
    const ultimo = focaveis[focaveis.length - 1];
    const ativo = document.activeElement;
    if (evento.shiftKey && (ativo === primeiro || ativo === janela)) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && ativo === ultimo) {
      evento.preventDefault();
      primeiro.focus();
    }
  }

  private devolverFoco(elemento: HTMLElement | null): void {
    setTimeout(() => elemento?.isConnected && elemento.focus());
  }

  // Leva o foco para a janela que abriu, para o teclado e o leitor de tela começarem nela
  private focarJanela(): void {
    setTimeout(() => (document.querySelector('.janela') as HTMLElement | null)?.focus());
  }
}
