import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface RegistroDeAuditoria {
  id: number;
  em: string;
  login: string | null;
  acao: string;
  entidade: string | null;
  entidadeId: string | null;
  detalhes: string | null;
  ip: string | null;
}

export interface PaginaDeAuditoria {
  itens: RegistroDeAuditoria[];
  total: number;
  pagina: number;
  tamanho: number;
}

@Injectable({ providedIn: 'root' })
export class AuditoriaService {
  private http = inject(HttpClient);

  listar(pagina: number, tamanho: number, acao: string): Observable<PaginaDeAuditoria> {
    const parametros = new HttpParams().set('pagina', pagina).set('tamanho', tamanho).set('acao', acao);
    return this.http.get<PaginaDeAuditoria>('/api/v1/auditoria', { params: parametros });
  }
}
