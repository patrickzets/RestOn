import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Atendimento } from './mesas.service';

export type StatusReserva = 'ATIVA' | 'CHEGOU' | 'CONCLUIDA' | 'CANCELADA' | 'NAO_COMPARECEU';

export interface Reserva {
  id: number;
  mesaId: number;
  mesaNumero: number;
  nomeCliente: string;
  telefone: string | null; // só gerente e administrador recebem o telefone
  qtdPessoas: number;
  inicio: string;
  fim: string;
  status: StatusReserva;
  atrasada: boolean;
  valorAntecipado: number; // quanto já foi pago antecipadamente (0 = nada)
  criadoEm: string;
}

export interface DadosDaReserva {
  mesaId: number;
  nomeCliente: string;
  telefone: string;
  qtdPessoas: number;
  inicio: string;
}

@Injectable({ providedIn: 'root' })
export class ReservasService {
  private http = inject(HttpClient);
  private base = '/api/v1/reservas';

  // Reservas que começam entre "de" e "ate" (datas ISO)
  listar(de: string, ate: string): Observable<Reserva[]> {
    return this.http.get<Reserva[]>(this.base, { params: new HttpParams().set('de', de).set('ate', ate) });
  }

  criar(dados: DadosDaReserva): Observable<Reserva> {
    return this.http.post<Reserva>(this.base, dados);
  }

  atualizar(id: number, dados: DadosDaReserva): Observable<Reserva> {
    return this.http.put<Reserva>(`${this.base}/${id}`, dados);
  }

  cancelar(id: number): Observable<Reserva> {
    return this.http.post<Reserva>(`${this.base}/${id}/cancelar`, {});
  }

  registrarChegada(id: number): Observable<Atendimento> {
    return this.http.post<Atendimento>(`${this.base}/${id}/chegada`, {});
  }
}
