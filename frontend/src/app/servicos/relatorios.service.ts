import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface ProdutoVendido {
  produto: string;
  categoria: string;
  quantidade: number;
  receita: number;
}

export interface VendaPorHora {
  hora: number; // 0 a 23, no fuso de São Paulo; só aparecem as horas com venda
  total: number;
}

export interface Painel {
  faturamentoHoje: number;
  comandasFechadasHoje: number;
  ticketMedioHoje: number;
  comandasAbertas: number;
  mesasOcupadas: number;
  mesasAtivas: number;
  pedidosEmAndamento: number;
  produtosComEstoqueBaixo: number;
  operadorDoCaixa: string | null; // null = caixa fechado
  vendasPorHora: VendaPorHora[];
  maisVendidosHoje: ProdutoVendido[];
}

export interface ResumoDeVendas {
  faturamento: number;
  comandas: number;
  ticketMedio: number;
  taxaDeServico: number;
  estornado: number;
  itensCancelados: number;
}

export interface VendaPorDia {
  data: string; // yyyy-MM-dd
  total: number;
  comandas: number;
}

export interface RecebidoPorMetodo {
  metodo: 'PIX' | 'CARTAO' | 'DINHEIRO';
  total: number;
  pagamentos: number;
}

export interface VendaPorGarcom {
  garcom: string;
  total: number;
  comandas: number;
}

export interface Vendas {
  de: string;
  ate: string;
  resumo: ResumoDeVendas;
  porDia: VendaPorDia[];
  porMetodo: RecebidoPorMetodo[];
  maisVendidos: ProdutoVendido[];
  porGarcom: VendaPorGarcom[];
}

@Injectable({ providedIn: 'root' })
export class RelatoriosService {
  private http = inject(HttpClient);

  painel(): Observable<Painel> {
    return this.http.get<Painel>('/api/v1/painel');
  }

  vendas(de?: string, ate?: string): Observable<Vendas> {
    let params = new HttpParams();
    if (de) params = params.set('de', de);
    if (ate) params = params.set('ate', ate);
    return this.http.get<Vendas>('/api/v1/relatorios/vendas', { params });
  }
}
