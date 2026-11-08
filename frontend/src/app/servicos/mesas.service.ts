import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type EstadoMesa = 'LIVRE' | 'OCUPADA' | 'AGUARDANDO_LIBERACAO';

export interface Mesa {
  id: number;
  numero: number;
  capacidade: number;
  estado: EstadoMesa;
  ativa: boolean;
  atendimentoId: number | null;
  atendimentoAbertoEm: string | null;
  reservaProxima: { id: number; nomeCliente: string; qtdPessoas: number; inicio: string } | null;
  comandasAbertas: number;
  pedidosProntos: number;
  setor: string; // Salão, Varanda, Balcão...
  abertoPor: number | null; // id do garçom que abriu o atendimento (filtro "Minhas")
}

export interface DadosDaMesa {
  numero: number;
  capacidade: number;
  ativa: boolean;
  setor: string;
}

export interface Atendimento {
  id: number;
  mesaId: number;
  mesaNumero: number;
  reservaId: number | null;
  abertoEm: string;
  encerradoEm: string | null;
}

@Injectable({ providedIn: 'root' })
export class MesasService {
  private http = inject(HttpClient);
  private base = '/api/v1/mesas';

  listar(): Observable<Mesa[]> {
    return this.http.get<Mesa[]>(this.base);
  }

  criar(dados: DadosDaMesa): Observable<Mesa> {
    return this.http.post<Mesa>(this.base, dados);
  }

  atualizar(id: number, dados: DadosDaMesa): Observable<Mesa> {
    return this.http.put<Mesa>(`${this.base}/${id}`, dados);
  }

  liberar(id: number): Observable<Mesa> {
    return this.http.post<Mesa>(`${this.base}/${id}/liberar`, {});
  }

  // Mesas que comportam o grupo e não têm reserva no horário (ignorarReserva serve ao editar uma reserva)
  disponiveis(inicio: string, pessoas: number, ignorarReserva?: number): Observable<Mesa[]> {
    let parametros = new HttpParams().set('inicio', inicio).set('pessoas', pessoas);
    if (ignorarReserva) parametros = parametros.set('ignorarReserva', ignorarReserva);
    return this.http.get<Mesa[]>(`${this.base}/disponibilidade`, { params: parametros });
  }

  abrirAtendimento(mesaId: number): Observable<Atendimento> {
    return this.http.post<Atendimento>(`${this.base}/${mesaId}/atendimentos`, {});
  }

  trocarMesa(atendimentoId: number, novaMesaId: number): Observable<Atendimento> {
    return this.http.post<Atendimento>(`/api/v1/atendimentos/${atendimentoId}/trocar-mesa`, { novaMesaId });
  }

  encerrarAtendimento(atendimentoId: number): Observable<Atendimento> {
    return this.http.post<Atendimento>(`/api/v1/atendimentos/${atendimentoId}/encerrar`, {});
  }
}
