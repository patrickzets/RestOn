import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { MODULOS } from '../modulos';
import { AuthService, Perfil } from '../servicos/auth.service';
import { LayoutPrincipal } from './layout-principal';

describe('LayoutPrincipal', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LayoutPrincipal],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  function criar(perfil: Perfil) {
    TestBed.inject(AuthService).usuario.set({ id: 1, nome: 'Maria Silva', login: 'maria', perfil, senhaProvisoria: false });
    const fixture = TestBed.createComponent(LayoutPrincipal);
    fixture.detectChanges();
    return fixture;
  }

  const titulos = (fixture: ReturnType<typeof criar>): string[] =>
    Array.from(fixture.nativeElement.querySelectorAll('a.nav') as NodeListOf<HTMLElement>).map((a) => a.textContent?.trim() ?? '');

  it('o administrador vê todos os módulos', () => {
    expect(titulos(criar('ADMIN')).length).toBe(MODULOS.length);
  });

  it('o gerente vê tudo, menos a auditoria', () => {
    const lista = titulos(criar('GERENTE'));
    expect(lista).toContain('Equipe');
    expect(lista).not.toContain('Auditoria');
  });

  it('o garçom só vê mesas, reservas e comandas', () => {
    expect(titulos(criar('GARCOM'))).toEqual(['Mesas', 'Reservas', 'Comandas']);
  });

  it('a cozinha só vê a cozinha', () => {
    expect(titulos(criar('COZINHA'))).toEqual(['Cozinha']);
  });

  it('o bartender só vê a cozinha', () => {
    expect(titulos(criar('BARTENDER'))).toEqual(['Cozinha']);
  });

  it('mostra o nome, as iniciais e o perfil de quem está logado', () => {
    const texto = criar('CAIXA').nativeElement.querySelector('.usuario').textContent;

    expect(texto).toContain('Maria Silva');
    expect(texto).toContain('MS');
    expect(texto).toContain('Caixa');
  });

  it('não mostra aviso quando a API responde ok', () => {
    const fixture = criar('ADMIN');
    TestBed.inject(HttpTestingController).expectOne('/api/v1/saude').flush({ status: 'ok', banco: true, versao: '0.0.1', horario: '' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.vivo')).toBeNull();
  });

  it('mostra "API offline" quando a API não responde', () => {
    const fixture = criar('ADMIN');
    TestBed.inject(HttpTestingController).expectOne('/api/v1/saude').error(new ProgressEvent('error'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.vivo').textContent).toContain('API offline');
  });
});
