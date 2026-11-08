import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { AuthService, Usuario } from '../servicos/auth.service';
import { autenticadoGuard, perfilGuard, visitanteGuard } from './guardas';

const ANA: Usuario = { id: 2, nome: 'Ana Souza', login: 'ana', perfil: 'GARCOM', senhaProvisoria: false };
const ESTADO = {} as RouterStateSnapshot;

function rota(perfis?: string[]): ActivatedRouteSnapshot {
  return { data: { perfis } } as unknown as ActivatedRouteSnapshot;
}

describe('guardas de rota', () => {
  let http: HttpTestingController;
  let router: Router;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    auth = TestBed.inject(AuthService);
  });

  const url = (resultado: unknown) => router.serializeUrl(resultado as UrlTree);

  it('sem login, o autenticadoGuard manda para /login', async () => {
    const resultado = TestBed.runInInjectionContext(() => autenticadoGuard(rota(), ESTADO)) as Promise<unknown>;
    http.expectOne('/api/v1/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(url(await resultado)).toBe('/login');
  });

  it('com senha provisória, o autenticadoGuard manda para /trocar-senha', async () => {
    const resultado = TestBed.runInInjectionContext(() => autenticadoGuard(rota(), ESTADO)) as Promise<unknown>;
    http.expectOne('/api/v1/auth/me').flush({ ...ANA, senhaProvisoria: true });

    expect(url(await resultado)).toBe('/trocar-senha');
  });

  it('logado e com a senha trocada, o autenticadoGuard deixa passar', async () => {
    const resultado = TestBed.runInInjectionContext(() => autenticadoGuard(rota(), ESTADO)) as Promise<unknown>;
    http.expectOne('/api/v1/auth/me').flush(ANA);

    expect(await resultado).toBe(true);
  });

  it('quem já entrou não vê a tela de login: vai para a página do seu perfil', async () => {
    const resultado = TestBed.runInInjectionContext(() => visitanteGuard(rota(), ESTADO)) as Promise<unknown>;
    http.expectOne('/api/v1/auth/me').flush(ANA);

    expect(url(await resultado)).toBe('/mesas');
  });

  it('o perfilGuard barra um perfil que não está na lista e manda para a página inicial dele', () => {
    auth.usuario.set(ANA);

    const resultado = TestBed.runInInjectionContext(() => perfilGuard(rota(['ADMIN', 'GERENTE']), ESTADO));

    expect(url(resultado)).toBe('/mesas');
  });

  it('o perfilGuard deixa passar um perfil permitido', () => {
    auth.usuario.set(ANA);

    expect(TestBed.runInInjectionContext(() => perfilGuard(rota(['GARCOM', 'ADMIN']), ESTADO))).toBe(true);
  });
});
