import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import type { Pagamento } from './caixa.service';
import { DestinoPreparo } from './cardapio.service';

export type StatusPedido = 'RECEBIDO' | 'EM_PREPARO' | 'PRONTO' | 'ENTREGUE' | 'CANCELADO';
export type StatusComanda = 'ABERTA' | 'FECHADA' | 'CANCELADA';

export const NOMES_DOS_STATUS: Record<StatusPedido, string> = {
  RECEBIDO: 'Recebido',
  EM_PREPARO: 'Em preparo',
  PRONTO: 'Pronto',
  ENTREGUE: 'Entregue',
  CANCELADO: 'Cancelado',
};

export interface ItemDoPedido {
  id: number;
  produtoId: number;
  produtoNome: string;
  quantidade: number;
  precoUnitario: number;
  subtotal: number;
  observacao: string | null;
  cancelado: boolean;
  motivoCancelamento: string | null;
  comandaId: number;
}

export interface Pedido {
  id: number;
  comandaId: number;
  comandaCodigo: string;
  numeroCliente: number;
  mesaNumero: number;
  destino: DestinoPreparo;
  status: StatusPedido;
  criadoEm: string;
  tempoPrevistoMin: number;
  itens: ItemDoPedido[];
}

export interface Comanda {
  id: number;
  codigo: string;
  atendimentoId: number;
  mesaId: number;
  mesaNumero: number;
  numeroCliente: number;
  nomeCliente: string | null;
  status: StatusComanda;
  taxaPercentual: number;
  taxaRemovida: boolean;
  subtotal: number;
  taxaValor: number;
  total: number;
  pago: number;
  restante: number;
  abertaEm: string;
  fechadaEm: string | null;
  pedidos: Pedido[] | null; // pedidos e pagamentos só vêm no detalhe da comanda
  pagamentos: Pagamento[] | null;
}

export interface ItemNovo {
  produtoId: number;
  quantidade: number;
  observacao: string;
}

export interface FiltrosDeComanda {
  atendimentoId?: number;
  mesa?: number;
  codigo?: string;
  status?: StatusComanda;
}

@Injectable({ providedIn: 'root' })
export class ComandasService {
  private http = inject(HttpClient);

  abrir(atendimentoId: number, nomeCliente = ''): Observable<Comanda> {
    return this.http.post<Comanda>(`/api/v1/atendimentos/${atendimentoId}/comandas`, { nomeCliente });
  }

  listar(filtros: FiltrosDeComanda = {}): Observable<Comanda[]> {
    let parametros = new HttpParams();
    if (filtros.atendimentoId) parametros = parametros.set('atendimentoId', filtros.atendimentoId);
    if (filtros.mesa) parametros = parametros.set('mesa', filtros.mesa);
    if (filtros.codigo) parametros = parametros.set('codigo', filtros.codigo);
    if (filtros.status) parametros = parametros.set('status', filtros.status);
    return this.http.get<Comanda[]>('/api/v1/comandas', { params: parametros });
  }

  detalhe(id: number): Observable<Comanda> {
    return this.http.get<Comanda>(`/api/v1/comandas/${id}`);
  }

  cancelar(id: number): Observable<Comanda> {
    return this.http.post<Comanda>(`/api/v1/comandas/${id}/cancelar`, {});
  }

  lancarPedido(comandaId: number, itens: ItemNovo[]): Observable<Pedido[]> {
    return this.http.post<Pedido[]>(`/api/v1/comandas/${comandaId}/pedidos`, { itens });
  }

  cancelarItem(itemId: number, motivo: string): Observable<Pedido> {
    return this.http.post<Pedido>(`/api/v1/itens/${itemId}/cancelar`, { motivo });
  }

  transferirItem(itemId: number, comandaDestinoId: number): Observable<ItemDoPedido> {
    return this.http.post<ItemDoPedido>(`/api/v1/itens/${itemId}/transferir`, { comandaDestinoId });
  }

  // Pedidos que ainda não foram entregues (cozinha, bar e o que espera o garçom)
  pedidosEmAndamento(destino?: DestinoPreparo): Observable<Pedido[]> {
    const parametros = destino ? new HttpParams().set('destino', destino) : new HttpParams();
    return this.http.get<Pedido[]>('/api/v1/cozinha/pedidos', { params: parametros });
  }

  mudarStatus(pedidoId: number, status: StatusPedido): Observable<Pedido> {
    return this.http.patch<Pedido>(`/api/v1/pedidos/${pedidoId}/status`, { status });
  }
}
