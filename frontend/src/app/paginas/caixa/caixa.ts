import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, forkJoin, merge } from 'rxjs';

import { AuthService } from '../../servicos/auth.service';
import { CaixaService, MetodoPagamento, NOMES_DOS_METODOS, Pagamento, Turno } from '../../servicos/caixa.service';
import { Comanda, ComandasService, Pedido, StatusComanda } from '../../servicos/comandas.service';
import { mensagemDoErro } from '../../servicos/erros';
import { TempoRealService } from '../../servicos/tempo-real.service';

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const METODOS: MetodoPagamento[] = ['PIX', 'CARTAO', 'DINHEIRO'];

@Component({
  selector: 'app-caixa',
  imports: [DatePipe, RouterLink],
  templateUrl: './caixa.html',
  styleUrl: './caixa.css',
})
export class Caixa implements OnInit {
  private api = inject(CaixaService);
  private comandasApi = inject(ComandasService);
  private auth = inject(AuthService);
  private tempoReal = inject(TempoRealService);
  private destroyRef = inject(DestroyRef);

  metodos = METODOS;
  nomesDosMetodos = NOMES_DOS_METODOS;

  turno = signal<Turno | null>(null);
  turnoCarregado = signal(false);
  resumoDoFechamento = signal<Turno | null>(null);
  valorInicial = signal<number | null>(0);
  fechandoCaixa = signal(false);
  valorContado = signal<number | null>(null);
  erroDoTurno = signal('');

  comandas = signal<Comanda[]>([]);
  carregando = signal(true);
  erroDaLista = signal('');
  filtro = signal<StatusComanda>('ABERTA');
  busca = signal('');

  // Quantos pedidos de cada comanda ainda estão na cozinha/bar (a lista não traz os pedidos, então buscamos o detalhe)
  naCozinha = signal<Record<number, number>>({});

  aberta = signal<Comanda | null>(null);
  trabalhando = signal(false);
  erroDoPainel = signal('');

  metodo = signal<MetodoPagamento>('PIX');
  valor = signal<number | null>(null);
  recebido = signal<number | null>(null);
  outroValor = signal(false); // mostra o campo livre do "Cliente entregou"
  confirmandoFechar = signal(false);
  estornando = signal<number | null>(null);
  motivoDoEstorno = signal('');
  alterandoTaxa = signal(false);
  motivoDaTaxa = signal('');

  // Estornar e mexer na taxa são decisões de gerente ou administrador
  podeGerir = computed(() => ['ADMIN', 'GERENTE'].includes(this.auth.usuario()?.perfil ?? ''));

  visiveis = computed(() => {
    const termo = this.busca().trim().toLowerCase();
    return this.comandas().filter(
      (c) => !termo || c.codigo.toLowerCase().includes(termo) || String(c.mesaNumero) === termo,
    );
  });

  // Pedido PRONTO já conta como entregue: só o que ainda está na cozinha/bar segura o fechamento
  pendentes = computed(() => (this.aberta()?.pedidos ?? []).filter(this.aindaNaCozinha));

  // Passo atual do modal: se falta pagar, é o pagamento; senão, fechar
  passo = computed<'pagamento' | 'fechar'>(() => ((this.aberta()?.restante ?? 0) > 0 ? 'pagamento' : 'fechar'));

  // Valores de notas para o "Cliente entregou": o exato e os próximos arredondamentos (10, 50 e 100)
  sugestoesDeNota = computed(() => {
    const valor = this.valor();
    if (valor === null || valor <= 0) return [];
    const redondos = [10, 50, 100].map((base) => Math.ceil(valor / base) * base);
    return [...new Set(redondos)].filter((n) => n > valor).slice(0, 3);
  });

  troco = computed(() => {
    const recebido = this.recebido();
    const valor = this.valor();
    return this.metodo() === 'DINHEIRO' && recebido !== null && valor !== null && recebido > valor ? recebido - valor : 0;
  });

  podeFechar = computed(() => {
    const c = this.aberta();
    return !!c && c.status === 'ABERTA' && c.total > 0 && c.restante <= 0 && this.pendentes().length === 0;
  });

  private elementoAnterior: HTMLElement | null = null;

  private aindaNaCozinha(p: Pedido): boolean {
    return ['RECEBIDO', 'EM_PREPARO'].includes(p.status) && p.itens.some((i) => !i.cancelado);
  }

  ngOnInit(): void {
    this.carregarTurno();
    this.carregar();
    merge(this.tempoReal.ouvir('mesas'), this.tempoReal.ouvir('pedidos'))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.recarregar());
  }

  carregarTurno(): void {
    this.api.turnoAtual().subscribe({
      next: (turno) => {
        this.turno.set(turno);
        this.turnoCarregado.set(true);
      },
      error: (erro: HttpErrorResponse) => {
        this.erroDoTurno.set(mensagemDoErro(erro));
        this.turnoCarregado.set(true);
      },
    });
  }

  abrirCaixa(): void {
    const inicial = this.valorInicial();
    if (inicial === null || inicial < 0) {
      this.erroDoTurno.set('Informe o valor inicial do caixa (pode ser zero).');
      return;
    }
    this.erroDoTurno.set('');
    this.api.abrirTurno(inicial).subscribe({
      next: (turno) => {
        this.turno.set(turno);
        this.resumoDoFechamento.set(null);
      },
      error: (erro: HttpErrorResponse) => this.erroDoTurno.set(mensagemDoErro(erro)),
    });
  }

  fecharCaixa(): void {
    const turno = this.turno();
    const contado = this.valorContado();
    if (!turno) return;
    if (contado === null || contado < 0) {
      this.erroDoTurno.set('Informe quanto você contou em dinheiro no caixa.');
      return;
    }
    this.erroDoTurno.set('');
    this.api.fecharTurno(turno.id, contado).subscribe({
      next: (fechado) => {
        this.resumoDoFechamento.set(fechado);
        this.turno.set(null);
        this.fechandoCaixa.set(false);
        this.valorContado.set(null);
      },
      error: (erro: HttpErrorResponse) => this.erroDoTurno.set(mensagemDoErro(erro)),
    });
  }

  carregar(): void {
    this.carregando.set(true);
    this.erroDaLista.set('');
    this.comandasApi.listar({ status: this.filtro() }).subscribe({
      next: (lista) => {
        this.comandas.set(lista);
        this.carregando.set(false);
        this.buscarPendencias(lista);
      },
      error: (erro: HttpErrorResponse) => {
        this.erroDaLista.set(mensagemDoErro(erro));
        this.carregando.set(false);
      },
    });
  }

  escolherFiltro(filtro: StatusComanda): void {
    this.filtro.set(filtro);
    this.carregar();
  }

  abrirDetalhe(id: number): void {
    this.limparPainel();
    this.comandasApi.detalhe(id).subscribe({
      next: (comanda) => {
        this.elementoAnterior = document.activeElement as HTMLElement | null;
        this.aberta.set(comanda);
        this.sugerirValor(comanda);
        // Leva o foco para dentro da janela, para o teclado e o leitor de tela começarem nela
        setTimeout(() => document.querySelector<HTMLElement>('.janela')?.focus());
      },
      error: (erro: HttpErrorResponse) => this.erroDaLista.set(mensagemDoErro(erro)),
    });
  }

  fechar(): void {
    this.aberta.set(null);
    this.limparPainel();
    this.elementoAnterior?.focus();
  }

  // Mantém o Tab dentro da janela enquanto ela está aberta
  prenderFoco(evento: Event): void {
    const e = evento as KeyboardEvent;
    const janela = e.currentTarget as HTMLElement;
    const focaveis = Array.from(janela.querySelectorAll<HTMLElement>('button:not([disabled]), a[href], input:not([disabled]), summary'));
    if (focaveis.length === 0) return;
    const primeiro = focaveis[0];
    const ultimo = focaveis[focaveis.length - 1];
    if (e.shiftKey && (document.activeElement === primeiro || document.activeElement === janela)) {
      e.preventDefault();
      ultimo.focus();
    } else if (!e.shiftKey && document.activeElement === ultimo) {
      e.preventDefault();
      primeiro.focus();
    }
  }

  escolherMetodo(metodo: MetodoPagamento): void {
    this.metodo.set(metodo);
    this.recebido.set(null);
    this.outroValor.set(false);
  }

  // Botão rápido do "Cliente entregou"
  entregou(valor: number): void {
    this.recebido.set(valor);
    this.outroValor.set(false);
  }

  // Cartões da lista: quanto falta e o aviso da cozinha
  pendenciasDe(id: number): number {
    return this.naCozinha()[id] ?? 0;
  }

  receber(): void {
    const comanda = this.aberta();
    const valor = this.valor();
    if (!comanda) return;
    if (valor === null || valor <= 0) {
      this.erroDoPainel.set('Informe o valor a receber.');
      return;
    }
    const dados = this.metodo() === 'DINHEIRO' && this.recebido() !== null
      ? { metodo: this.metodo(), valor, valorRecebido: this.recebido()! }
      : { metodo: this.metodo(), valor };
    this.executar(this.api.pagar(comanda.id, dados));
  }

  fecharComanda(): void {
    const comanda = this.aberta();
    if (comanda) this.executar(this.api.fecharComanda(comanda.id), () => this.confirmandoFechar.set(false));
  }

  comecarAEstornar(pagamento: Pagamento): void {
    this.erroDoPainel.set('');
    this.estornando.set(pagamento.id);
  }

  confirmarEstorno(): void {
    const id = this.estornando();
    if (id === null) return;
    if (this.motivoDoEstorno().trim().length < 3) {
      this.erroDoPainel.set('Informe o motivo do estorno (pelo menos 3 letras).');
      return;
    }
    this.executar(this.api.estornar(id, this.motivoDoEstorno()));
  }

  confirmarTaxa(removida: boolean): void {
    const comanda = this.aberta();
    if (!comanda) return;
    if (removida && this.motivoDaTaxa().trim().length < 3) {
      this.erroDoPainel.set('Informe o motivo para remover a taxa de serviço.');
      return;
    }
    this.executar(this.api.alterarTaxa(comanda.id, removida, this.motivoDaTaxa()));
  }

  cancelarEdicao(): void {
    this.estornando.set(null);
    this.alterandoTaxa.set(false);
    this.confirmandoFechar.set(false);
    this.motivoDoEstorno.set('');
    this.motivoDaTaxa.set('');
    this.erroDoPainel.set('');
  }

  reais(valor: number): string {
    return MOEDA.format(valor);
  }

  numero(evento: Event): number | null {
    const texto = (evento.target as HTMLInputElement).value;
    return texto === '' ? null : Number(texto);
  }

  private recarregar(): void {
    const atual = this.aberta();
    this.api.turnoAtual().subscribe((turno) => this.turno.set(turno));
    this.comandasApi.listar({ status: this.filtro() }).subscribe((lista) => {
      this.comandas.set(lista);
      this.buscarPendencias(lista);
    });
    if (atual) {
      this.comandasApi.detalhe(atual.id).subscribe((comanda) => {
        this.aberta.set(comanda);
        if (comanda.status !== 'ABERTA') this.valor.set(null);
      });
    }
  }

  // Depois de cada ação, recarrega a comanda e já sugere cobrar o que falta
  private executar(chamada: Observable<unknown>, depois?: () => void): void {
    this.trabalhando.set(true);
    this.erroDoPainel.set('');
    chamada.subscribe({
      next: () => {
        this.trabalhando.set(false);
        this.cancelarEdicao();
        depois?.();
        const comanda = this.aberta();
        this.api.turnoAtual().subscribe((turno) => this.turno.set(turno));
        this.comandasApi.listar({ status: this.filtro() }).subscribe((lista) => this.comandas.set(lista));
        if (comanda) {
          this.comandasApi.detalhe(comanda.id).subscribe((atualizada) => {
            this.aberta.set(atualizada);
            this.sugerirValor(atualizada);
          });
        }
      },
      error: (erro: HttpErrorResponse) => {
        this.trabalhando.set(false);
        this.erroDoPainel.set(mensagemDoErro(erro));
      },
    });
  }

  private sugerirValor(comanda: Comanda): void {
    this.valor.set(comanda.restante > 0 ? comanda.restante : null);
    this.recebido.set(null);
    this.outroValor.set(false);
  }

  // Só as comandas abertas com conta precisam do aviso "ainda na cozinha"
  private buscarPendencias(lista: Comanda[]): void {
    const abertas = lista.filter((c) => c.status === 'ABERTA' && c.total > 0);
    if (abertas.length === 0) {
      this.naCozinha.set({});
      return;
    }
    forkJoin(abertas.map((c) => this.comandasApi.detalhe(c.id))).subscribe({
      next: (detalhes) => {
        const mapa: Record<number, number> = {};
        for (const d of detalhes) mapa[d.id] = (d.pedidos ?? []).filter(this.aindaNaCozinha).length;
        this.naCozinha.set(mapa);
      },
      error: () => this.naCozinha.set({}), // o aviso é só um extra: se falhar, some em vez de mostrar um valor velho
    });
  }

  private limparPainel(): void {
    this.cancelarEdicao();
    this.trabalhando.set(false);
    this.metodo.set('PIX');
  }
}
