import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import type { Comanda, StatusComanda } from './comandas.service';

export type MetodoPagamento = 'PIX' | 'CARTAO' | 'DINHEIRO';

export const NOMES_DOS_METODOS: Record<MetodoPagamento, string> = {
  PIX: 'PIX',
  CARTAO: 'Cartão',
  DINHEIRO: 'Dinheiro',
};

export interface Pagamento {
  id: number;
  comandaId: number | null;
  reservaId: number | null;
  metodo: MetodoPagamento;
  origem: 'CAIXA' | 'RESERVA';
  valor: number;
  valorRecebido: number;
  troco: number;
  status: 'CONFIRMADO' | 'ESTORNADO';
  criadoEm: string;
}

export interface Turno {
  id: number;
  operadorId: number;
  operadorNome: string;
  abertoEm: string;
  fechadoEm: string | null;
  valorInicial: number;
  valorContado: number | null;
  status: 'ABERTO' | 'FECHADO';
  totalPix: number;
  totalCartao: number;
  totalDinheiro: number;
  totalRecebido: number;
  qtdPagamentos: number;
  dinheiroEsperado: number;
  diferenca: number | null; // contado - esperado: negativo = faltou, positivo = sobrou
}

export interface Comprovante {
  codigo: string;
  status: StatusComanda;
  mesaNumero: number;
  numeroCliente: number;
  nomeCliente: string | null;
  abertaEm: string;
  fechadaEm: string | null;
  itens: { produto: string; quantidade: number; precoUnitario: number; subtotal: number; observacao: string | null }[];
  subtotal: number;
  taxaPercentual: number;
  taxaRemovida: boolean;
  taxaValor: number;
  total: number;
  pagamentos: Pagamento[];
  pago: number;
  troco: number;
}

export interface NovoPagamento {
  metodo: MetodoPagamento;
  valor: number;
  valorRecebido?: number;
}

@Injectable({ providedIn: 'root' })
export class CaixaService {
  private http = inject(HttpClient);

  // Sem caixa aberto, a API responde 204 e o resultado é null
  turnoAtual(): Observable<Turno | null> {
    return this.http.get<Turno | null>('/api/v1/caixa/turno-atual');
  }

  historicoDeTurnos(): Observable<Turno[]> {
    return this.http.get<Turno[]>('/api/v1/caixa/turnos');
  }

  abrirTurno(valorInicial: number): Observable<Turno> {
    return this.http.post<Turno>('/api/v1/caixa/turnos', { valorInicial });
  }

  fecharTurno(id: number, valorContado: number): Observable<Turno> {
    return this.http.post<Turno>(`/api/v1/caixa/turnos/${id}/fechar`, { valorContado });
  }

  pagar(comandaId: number, dados: NovoPagamento): Observable<Pagamento> {
    return this.http.post<Pagamento>(`/api/v1/comandas/${comandaId}/pagamentos`, dados);
  }

  fecharComanda(comandaId: number): Observable<Comanda> {
    return this.http.post<Comanda>(`/api/v1/comandas/${comandaId}/fechar`, {});
  }

  estornar(pagamentoId: number, motivo: string): Observable<Pagamento> {
    return this.http.post<Pagamento>(`/api/v1/pagamentos/${pagamentoId}/estornar`, { motivo });
  }

  alterarTaxa(comandaId: number, removida: boolean, motivo: string): Observable<Comanda> {
    return this.http.patch<Comanda>(`/api/v1/comandas/${comandaId}/taxa`, { removida, motivo });
  }

  comprovante(comandaId: number): Observable<Comprovante> {
    return this.http.get<Comprovante>(`/api/v1/comandas/${comandaId}/comprovante`);
  }

  anteciparReserva(reservaId: number, metodo: MetodoPagamento, valor: number): Observable<Pagamento> {
    return this.http.post<Pagamento>(`/api/v1/reservas/${reservaId}/pagamento-antecipado`, { metodo, valor });
  }
}
