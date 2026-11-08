import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type SituacaoDoEstoque = 'OK' | 'BAIXO' | 'ZERADO';

export interface ItemDeEstoque {
  produtoId: number;
  nome: string;
  categoriaNome: string;
  ativo: boolean;
  controla: boolean;
  quantidade: number;
  minimo: number;
  situacao: SituacaoDoEstoque | null;
}

export type TipoDeMovimentacao = 'ENTRADA' | 'AJUSTE' | 'SAIDA_PEDIDO' | 'DEVOLUCAO';

export const NOMES_DAS_MOVIMENTACOES: Record<TipoDeMovimentacao, string> = {
  ENTRADA: 'Entrada',
  AJUSTE: 'Ajuste',
  SAIDA_PEDIDO: 'Saída por pedido',
  DEVOLUCAO: 'Devolução',
};

export interface Movimentacao {
  id: number;
  tipo: TipoDeMovimentacao;
  variacao: number;
  saldoApos: number;
  motivo: string | null;
  funcionario: string | null;
  criadoEm: string;
}

@Injectable({ providedIn: 'root' })
export class EstoqueService {
  private http = inject(HttpClient);
  private base = '/api/v1/estoque';

  listar(): Observable<ItemDeEstoque[]> {
    return this.http.get<ItemDeEstoque[]>(this.base);
  }

  historico(produtoId: number): Observable<Movimentacao[]> {
    return this.http.get<Movimentacao[]>(`${this.base}/${produtoId}/movimentacoes`);
  }

  configurar(produtoId: number, controla: boolean, minimo: number, quantidadeInicial?: number): Observable<ItemDeEstoque> {
    return this.http.put<ItemDeEstoque>(`${this.base}/${produtoId}/controle`, { controla, minimo, quantidadeInicial });
  }

  entrada(produtoId: number, quantidade: number, observacao: string): Observable<ItemDeEstoque> {
    return this.http.post<ItemDeEstoque>(`${this.base}/${produtoId}/entradas`, { quantidade, observacao });
  }

  ajustar(produtoId: number, quantidade: number, motivo: string): Observable<ItemDeEstoque> {
    return this.http.post<ItemDeEstoque>(`${this.base}/${produtoId}/ajustes`, { quantidade, motivo });
  }
}
