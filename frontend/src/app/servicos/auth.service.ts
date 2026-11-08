import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, firstValueFrom, of, tap } from 'rxjs';

export type Perfil = 'ADMIN' | 'GERENTE' | 'GARCOM' | 'CAIXA' | 'COZINHA' | 'BARTENDER';

export const NOMES_DOS_PERFIS: Record<Perfil, string> = {
  ADMIN: 'Administrador',
  GERENTE: 'Gerente',
  GARCOM: 'Garçom',
  CAIXA: 'Caixa',
  COZINHA: 'Cozinha',
  BARTENDER: 'Bartender',
};

export interface Usuario {
  id: number;
  nome: string;
  login: string;
  perfil: Perfil;
  senhaProvisoria: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  usuario = signal<Usuario | null>(null);
  private jaPerguntou = false;

  // Só pergunta uma vez. Essa primeira chamada também faz o servidor mandar o cookie CSRF.
  async carregar(): Promise<Usuario | null> {
    if (!this.jaPerguntou) {
      this.jaPerguntou = true;
      const usuario = await firstValueFrom(this.http.get<Usuario>('/api/v1/auth/me').pipe(catchError(() => of(null))));
      this.usuario.set(usuario);
    }
    return this.usuario();
  }

  entrar(login: string, senha: string): Observable<Usuario> {
    return this.http.post<Usuario>('/api/v1/auth/login', { login, senha }).pipe(
      tap((usuario) => {
        this.jaPerguntou = true;
        this.usuario.set(usuario);
      }),
    );
  }

  sair(): Observable<void> {
    return this.http.post<void>('/api/v1/auth/logout', {}).pipe(tap(() => this.limpar()));
  }

  alterarSenha(senhaAtual: string, novaSenha: string): Observable<void> {
    return this.http.post<void>('/api/v1/auth/alterar-senha', { senhaAtual, novaSenha }).pipe(
      tap(() => {
        const atual = this.usuario();
        if (atual) this.usuario.set({ ...atual, senhaProvisoria: false });
      }),
    );
  }

  limpar(): void {
    this.usuario.set(null);
  }

  paginaInicial(perfil: Perfil): string {
    switch (perfil) {
      case 'GARCOM':
        return '/mesas';
      case 'CAIXA':
        return '/caixa';
      case 'COZINHA':
      case 'BARTENDER':
        return '/cozinha';
      default:
        return '/visao-geral';
    }
  }
}
