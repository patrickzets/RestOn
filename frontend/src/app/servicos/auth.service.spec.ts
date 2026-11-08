import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService, Usuario } from './auth.service';

const MARIA: Usuario = { id: 1, nome: 'Maria Silva', login: 'maria', perfil: 'GARCOM', senhaProvisoria: false };

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  it('carregar guarda o usuário quando já existe uma sessão', async () => {
    const promessa = auth.carregar();
    http.expectOne('/api/v1/auth/me').flush(MARIA);

    expect(await promessa).toEqual(MARIA);
    expect(auth.usuario()).toEqual(MARIA);
  });

  it('carregar sem sessão (401) deixa o usuário vazio e só pergunta uma vez', async () => {
    const primeira = auth.carregar();
    http.expectOne('/api/v1/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(await primeira).toBeNull();

    expect(await auth.carregar()).toBeNull();
    http.expectNone('/api/v1/auth/me');
  });

  it('entrar guarda o usuário que o servidor devolveu', () => {
    auth.entrar('maria', 'senha-qualquer-1').subscribe();
    const chamada = http.expectOne('/api/v1/auth/login');
    expect(chamada.request.body).toEqual({ login: 'maria', senha: 'senha-qualquer-1' });
    chamada.flush(MARIA);

    expect(auth.usuario()?.login).toBe('maria');
  });

  it('entrar com senha errada não guarda ninguém', () => {
    let status = 0;
    auth.entrar('maria', 'errada').subscribe({ error: (e) => (status = e.status) });
    http.expectOne('/api/v1/auth/login').flush({ detail: 'Login ou senha inválidos.' }, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(auth.usuario()).toBeNull();
  });

  it('sair esquece o usuário', () => {
    auth.usuario.set(MARIA);
    auth.sair().subscribe();
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });

    expect(auth.usuario()).toBeNull();
  });

  it('trocar a senha tira a marca de senha provisória', () => {
    auth.usuario.set({ ...MARIA, senhaProvisoria: true });
    auth.alterarSenha('provisoria', 'NovaSenha#2026').subscribe();
    http.expectOne('/api/v1/auth/alterar-senha').flush(null, { status: 204, statusText: 'No Content' });

    expect(auth.usuario()?.senhaProvisoria).toBe(false);
  });

  it('cada perfil tem a sua página inicial', () => {
    expect(auth.paginaInicial('ADMIN')).toBe('/visao-geral');
    expect(auth.paginaInicial('GERENTE')).toBe('/visao-geral');
    expect(auth.paginaInicial('GARCOM')).toBe('/mesas');
    expect(auth.paginaInicial('CAIXA')).toBe('/caixa');
    expect(auth.paginaInicial('COZINHA')).toBe('/cozinha');
  });
});
