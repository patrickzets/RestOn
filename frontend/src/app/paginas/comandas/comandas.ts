import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, concat, toArray } from 'rxjs';

import { NOMES_DOS_DESTINOS } from '../../servicos/cardapio.service';
import { AuthService } from '../../servicos/auth.service';
import { Comanda, ComandasService, ItemDoPedido, NOMES_DOS_STATUS, Pedido, StatusComanda } from '../../servicos/comandas.service';
import { mensagemDoErro } from '../../servicos/erros';
import { TempoRealService } from '../../servicos/tempo-real.service';

type Filtro = StatusComanda | 'TODAS';

interface GrupoDaMesa {
  mesaNumero: number;
  comandas: Comanda[];
  minutosSentados: number | null; // da comanda aberta mais antiga; null se não houver aberta
}

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

@Component({
  selector: 'app-comandas',
  imports: [DatePipe, FormsModule, NgTemplateOutlet, RouterLink],
  templateUrl: './comandas.html',
  styleUrl: './comandas.css',
})
export class Comandas implements OnInit {
  private api = inject(ComandasService);
  private auth = inject(AuthService);
  private rota = inject(ActivatedRoute);
  private tempoReal = inject(TempoRealService);
  private destroyRef = inject(DestroyRef);

  nomesDosStatus = NOMES_DOS_STATUS;
  nomesDosDestinos = NOMES_DOS_DESTINOS;

  comandas = signal<Comanda[]>([]);
  carregando = signal(true);
  erroDaLista = signal('');
  filtro = signal<Filtro>('ABERTA');
  busca = signal('');

  aberta = signal<Comanda | null>(null); // comanda com o detalhe aberto na gaveta
  trabalhando = signal(false);
  erroDoPainel = signal('');
  confirmandoCancelarComanda = signal(false);

  pedidosAtivos = signal<Pedido[]>([]); // pedidos em andamento de todas as comandas (para os selos da lista)
  pedidoCancelando = signal<number | null>(null);
  mostrarAnteriores = signal(false);

  itemCancelando = signal<number | null>(null);
  motivo = signal('');
  itemTransferindo = signal<number | null>(null);
  destinoDaTransferencia = signal<number | null>(null);
  outrasComandas = signal<Comanda[]>([]);

  // Caixa só consulta; quem atende a mesa lança, cancela e transfere
  podeOperar = computed(() => ['ADMIN', 'GERENTE', 'GARCOM'].includes(this.auth.usuario()?.perfil ?? ''));
  ehGestor = computed(() => ['ADMIN', 'GERENTE'].includes(this.auth.usuario()?.perfil ?? ''));

  visiveis = computed(() => {
    const termo = this.busca().trim().toLowerCase();
    return this.comandas().filter(
      (c) => !termo || c.codigo.toLowerCase().includes(termo) || String(c.mesaNumero) === termo,
    );
  });

  // Agrupa por mesa, mesas em ordem numérica e comandas pelo número do cliente
  grupos = computed<GrupoDaMesa[]>(() => {
    const porMesa = new Map<number, Comanda[]>();
    for (const c of this.visiveis()) porMesa.set(c.mesaNumero, [...(porMesa.get(c.mesaNumero) ?? []), c]);
    return [...porMesa.entries()]
      .sort((a, b) => a[0] - b[0])
      .map(([mesaNumero, lista]) => {
        const abertas = lista.filter((c) => c.status === 'ABERTA');
        return {
          mesaNumero,
          comandas: [...lista].sort((a, b) => a.numeroCliente - b.numeroCliente),
          minutosSentados: abertas.length ? Math.max(...abertas.map((c) => this.minutosAberta(c))) : null,
        };
      });
  });

  // Detalhe: pronto primeiro, depois cozinha/bar, e o resto fica em "Anteriores"
  pedidosProntos = computed(() => (this.aberta()?.pedidos ?? []).filter((p) => p.status === 'PRONTO'));
  pedidosNaCozinha = computed(() => (this.aberta()?.pedidos ?? []).filter((p) => p.status === 'RECEBIDO' || p.status === 'EM_PREPARO'));
  pedidosAnteriores = computed(() => (this.aberta()?.pedidos ?? []).filter((p) => p.status === 'ENTREGUE' || p.status === 'CANCELADO'));

  ngOnInit(): void {
    this.carregar();
    this.carregarPedidos();
    this.tempoReal.ouvir('pedidos').pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.recarregar());

    // Vindo da tela de mesas: abre direto a comanda escolhida
    const abrir = Number(this.rota.snapshot.queryParamMap.get('abrir'));
    if (abrir) this.abrirDetalhe(abrir);
  }

  carregar(): void {
    this.carregando.set(true);
    this.erroDaLista.set('');
    const filtro = this.filtro();
    this.api.listar({ status: filtro === 'TODAS' ? undefined : filtro }).subscribe({
      next: (lista) => {
        this.comandas.set(lista);
        this.carregando.set(false);
      },
      error: (erro: HttpErrorResponse) => {
        this.erroDaLista.set(mensagemDoErro(erro));
        this.carregando.set(false);
      },
    });
  }

  // Uma chamada só para todas as comandas; agrupamos por comandaId no resumoDe
  private carregarPedidos(): void {
    this.api.pedidosEmAndamento().subscribe({
      next: (lista) => this.pedidosAtivos.set(lista),
      error: () => this.pedidosAtivos.set([]), // os selos são um extra, a lista funciona sem eles
    });
  }

  resumoDe(comanda: Comanda): { prontos: number; naCozinha: number } {
    const dela = this.pedidosAtivos().filter((p) => p.comandaId === comanda.id);
    return {
      prontos: dela.filter((p) => p.status === 'PRONTO').length,
      naCozinha: dela.filter((p) => p.status === 'RECEBIDO' || p.status === 'EM_PREPARO').length,
    };
  }

  escolherFiltro(filtro: Filtro): void {
    this.filtro.set(filtro);
    this.carregar();
  }

  // Quem abriu o detalhe: o foco volta para lá quando ele fecha
  private origem: HTMLElement | null = null;

  abrirDetalhe(id: number): void {
    this.origem = document.activeElement as HTMLElement | null;
    this.limparPainel();
    this.api.detalhe(id).subscribe({
      next: (comanda) => {
        this.aberta.set(comanda);
        // Em tablet e celular o detalhe é uma tela cheia: o foco começa nela
        if (this.telaEstreita()) setTimeout(() => (document.querySelector('.gaveta') as HTMLElement | null)?.focus());
      },
      error: (erro: HttpErrorResponse) => this.erroDaLista.set(mensagemDoErro(erro)),
    });
  }

  fechar(): void {
    this.aberta.set(null);
    this.mostrarAnteriores.set(false);
    this.limparPainel();
    const origem = this.origem;
    setTimeout(() => origem?.isConnected && origem.focus());
  }

  // Esc fecha o detalhe
  @HostListener('document:keydown.escape')
  aoApertarEsc(): void {
    if (this.aberta()) this.fechar();
  }

  // Só prende o Tab quando o detalhe cobre a tela inteira (mesmo limite do CSS)
  prenderFoco(evento: KeyboardEvent): void {
    if (evento.key !== 'Tab' || !this.telaEstreita()) return;
    const gaveta = evento.currentTarget as HTMLElement;
    const focaveis = Array.from(gaveta.querySelectorAll<HTMLElement>('a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), summary'));
    if (focaveis.length === 0) return;
    const primeiro = focaveis[0];
    const ultimo = focaveis[focaveis.length - 1];
    const ativo = document.activeElement;
    if (evento.shiftKey && (ativo === primeiro || ativo === gaveta)) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && ativo === ultimo) {
      evento.preventDefault();
      primeiro.focus();
    }
  }

  private telaEstreita(): boolean {
    return window.innerWidth <= 1000;
  }

  comecarACancelarPedido(pedido: Pedido): void {
    this.limparPainel();
    this.pedidoCancelando.set(pedido.id);
  }

  // Cancela todos os itens ainda ativos do pedido, um por vez, e só recarrega no fim
  confirmarCancelamentoDoPedido(pedido: Pedido): void {
    if (this.motivo().trim().length < 3) {
      this.erroDoPainel.set('Informe o motivo do cancelamento (pelo menos 3 letras).');
      return;
    }
    const motivo = this.motivo();
    const chamadas = pedido.itens.filter((i) => !i.cancelado).map((i) => this.api.cancelarItem(i.id, motivo));
    this.executar(concat(...chamadas).pipe(toArray()));
  }

  // O pedido é cancelável se ainda tiver pelo menos um item cancelável
  pedidoCancelavel(pedido: Pedido): boolean {
    return pedido.itens.some((i) => this.podeCancelar(pedido, i));
  }

  comecarACancelarItem(item: ItemDoPedido): void {
    this.limparPainel();
    this.itemCancelando.set(item.id);
  }

  confirmarCancelamentoDoItem(): void {
    const item = this.itemCancelando();
    if (item === null) return;
    if (this.motivo().trim().length < 3) {
      this.erroDoPainel.set('Informe o motivo do cancelamento (pelo menos 3 letras).');
      return;
    }
    this.executar(this.api.cancelarItem(item, this.motivo()));
  }

  comecarATransferir(item: ItemDoPedido): void {
    const atual = this.aberta();
    if (!atual) return;
    this.limparPainel();
    this.itemTransferindo.set(item.id);
    this.api.listar({ atendimentoId: atual.atendimentoId, status: 'ABERTA' }).subscribe({
      next: (lista) => this.outrasComandas.set(lista.filter((c) => c.id !== atual.id)),
      error: (erro: HttpErrorResponse) => this.erroDoPainel.set(mensagemDoErro(erro)),
    });
  }

  confirmarTransferencia(): void {
    const item = this.itemTransferindo();
    const destino = this.destinoDaTransferencia();
    if (item !== null && destino !== null) this.executar(this.api.transferirItem(item, destino));
  }

  cancelarComanda(): void {
    const comanda = this.aberta();
    if (comanda) this.executar(this.api.cancelar(comanda.id), () => this.confirmandoCancelarComanda.set(false));
  }

  cancelarEdicao(): void {
    this.limparPainel();
  }

  nomeDoDestino(destino: keyof typeof NOMES_DOS_DESTINOS): string {
    return NOMES_DOS_DESTINOS[destino];
  }

  preco(valor: number): string {
    return MOEDA.format(valor);
  }

  // 45 vira "45 min"; 242 vira "4 h 02"
  duracao(minutos: number): string {
    if (minutos < 60) return minutos + ' min';
    const resto = minutos % 60;
    return Math.floor(minutos / 60) + ' h' + (resto ? ' ' + String(resto).padStart(2, '0') : '');
  }

  minutosAberta(comanda: Comanda): number {
    return Math.max(0, Math.floor((Date.now() - new Date(comanda.abertaEm).getTime()) / 60000));
  }

  // Soma só os itens que não foram cancelados
  subtotalDoPedido(pedido: Pedido): number {
    return pedido.itens.filter((i) => !i.cancelado).reduce((soma, i) => soma + i.subtotal, 0);
  }

  rotuloDoCliente(c: Comanda): string {
    return 'Comanda ' + c.numeroCliente;
  }

  // Dá para cancelar o item enquanto a comanda está aberta e o pedido não foi entregue (a API confere o resto)
  podeCancelar(pedido: Pedido, item: ItemDoPedido): boolean {
    const emAberto = this.aberta()?.status === 'ABERTA' && !item.cancelado && pedido.status !== 'ENTREGUE' && pedido.status !== 'CANCELADO';
    // Depois que a cozinha começou ou marcou pronto, só o gerente ou o administrador cancela (a API também recusa o garçom)
    return this.podeOperar() && emAberto && (this.ehGestor() || pedido.status === 'RECEBIDO');
  }

  semItensAtivos(comanda: Comanda): boolean {
    return (comanda.pedidos ?? []).every((p) => p.itens.every((i) => i.cancelado));
  }

  private recarregar(): void {
    const atual = this.aberta();
    this.api.listar({ status: this.filtro() === 'TODAS' ? undefined : (this.filtro() as StatusComanda) }).subscribe((lista) => this.comandas.set(lista));
    this.carregarPedidos();
    if (atual) this.api.detalhe(atual.id).subscribe((comanda) => this.aberta.set(comanda));
  }

  // Roda uma ação da API e atualiza a lista e o detalhe quando dá certo
  private executar(chamada: Observable<unknown>, depois?: () => void): void {
    this.trabalhando.set(true);
    this.erroDoPainel.set('');
    chamada.subscribe({
      next: () => {
        this.trabalhando.set(false);
        this.itemCancelando.set(null);
        this.pedidoCancelando.set(null);
        this.itemTransferindo.set(null);
        this.motivo.set('');
        depois?.();
        this.recarregar();
      },
      error: (erro: HttpErrorResponse) => {
        this.trabalhando.set(false);
        this.erroDoPainel.set(mensagemDoErro(erro));
      },
    });
  }

  private limparPainel(): void {
    this.erroDoPainel.set('');
    this.trabalhando.set(false);
    this.itemCancelando.set(null);
    this.pedidoCancelando.set(null);
    this.itemTransferindo.set(null);
    this.destinoDaTransferencia.set(null);
    this.motivo.set('');
    this.confirmandoCancelarComanda.set(false);
  }
}
