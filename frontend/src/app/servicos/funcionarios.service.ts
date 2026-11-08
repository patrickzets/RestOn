import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Perfil } from './auth.service';

export interface Funcionario {
  id: number;
  nome: string;
  login: string;
  perfil: Perfil;
  ativo: boolean;
  bloqueado: boolean;
  senhaProvisoria: boolean;
  ultimoAcessoEm: string | null;
  criadoEm: string;
}

export interface FuncionarioCriado {
  funcionario: Funcionario;
  senhaProvisoria: string;
}

export type AcaoDeConta = 'bloquear' | 'desbloquear' | 'desativar' | 'ativar';

@Injectable({ providedIn: 'root' })
export class FuncionariosService {
  private http = inject(HttpClient);
  private base = '/api/v1/funcionarios';

  listar(): Observable<Funcionario[]> {
    return this.http.get<Funcionario[]>(this.base);
  }

  criar(dados: { nome: string; login: string; perfil: Perfil }): Observable<FuncionarioCriado> {
    return this.http.post<FuncionarioCriado>(this.base, dados);
  }

  atualizar(id: number, dados: { nome: string; perfil: Perfil }): Observable<Funcionario> {
    return this.http.put<Funcionario>(`${this.base}/${id}`, dados);
  }

  mudarSituacao(id: number, acao: AcaoDeConta): Observable<Funcionario> {
    return this.http.patch<Funcionario>(`${this.base}/${id}/${acao}`, {});
  }

  redefinirSenha(id: number): Observable<{ senhaProvisoria: string }> {
    return this.http.post<{ senhaProvisoria: string }>(`${this.base}/${id}/redefinir-senha`, {});
  }
}
