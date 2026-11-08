import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AuthService, Perfil } from '../../servicos/auth.service';
import { Funcionario } from '../../servicos/funcionarios.service';
import { Equipe } from './equipe';

const FUNCIONARIOS: Funcionario[] = [
  { id: 1, nome: 'Administrador', login: 'admin', perfil: 'ADMIN', ativo: true, bloqueado: false, senhaProvisoria: false, ultimoAcessoEm: '2026-11-05T12:00:00Z', criadoEm: '2026-11-05T10:00:00Z' },
  { id: 2, nome: 'Ana Souza', login: 'ana', perfil: 'GARCOM', ativo: true, bloqueado: true, senhaProvisoria: false, ultimoAcessoEm: null, criadoEm: '2026-11-05T10:30:00Z' },
  { id: 3, nome: 'Bruno Lima', login: 'bruno', perfil: 'CAIXA', ativo: false, bloqueado: false, senhaProvisoria: false, ultimoAcessoEm: null, criadoEm: '2026-11-05T10:40:00Z' },
];

describe('Equipe', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Equipe],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function abrir(perfil: Perfil): ComponentFixture<Equipe> {
    TestBed.inject(AuthService).usuario.set({ id: 1, nome: 'Quem Mexe', login: 'quem', perfil, senhaProvisoria: false });
    const fixture = TestBed.createComponent(Equipe);
    fixture.detectChanges();
    http.expectOne('/api/v1/funcionarios').flush(FUNCIONARIOS);
    fixture.detectChanges();
    return fixture;
  }

  it('lista os funcionários com a situação de cada um', () => {
    const texto = (abrir('ADMIN').nativeElement as HTMLElement).textContent;

    expect(texto).toContain('Ana Souza');
    expect(texto).toContain('Bloqueado');
    expect(texto).toContain('Desativado');
    expect(texto).toContain('Nunca entrou');
  });

  it('o gerente não vê a opção Administrador ao criar um funcionário', () => {
    const fixture = abrir('GERENTE');
    (fixture.nativeElement.querySelector('.titulo .btn') as HTMLButtonElement).click();
    fixture.detectChanges();

    const opcoes = Array.from(fixture.nativeElement.querySelectorAll('#perfil option') as NodeListOf<HTMLOptionElement>).map((o) => o.textContent?.trim());
    expect(opcoes).toContain('Garçom');
    expect(opcoes).not.toContain('Administrador');
  });

  it('o administrador vê a opção Administrador', () => {
    const fixture = abrir('ADMIN');
    (fixture.nativeElement.querySelector('.titulo .btn') as HTMLButtonElement).click();
    fixture.detectChanges();

    const opcoes = Array.from(fixture.nativeElement.querySelectorAll('#perfil option') as NodeListOf<HTMLOptionElement>).map((o) => o.textContent?.trim());
    expect(opcoes).toContain('Administrador');
  });

  it('o gerente pode criar um bartender', () => {
    const fixture = abrir('GERENTE');
    (fixture.nativeElement.querySelector('.titulo .btn') as HTMLButtonElement).click();
    fixture.detectChanges();

    const opcoes = Array.from(fixture.nativeElement.querySelectorAll('#perfil option') as NodeListOf<HTMLOptionElement>).map((o) => o.textContent?.trim());
    expect(opcoes).toContain('Bartender');
  });

  it('mostra a senha provisória uma vez depois de criar um funcionário', () => {
    const fixture = abrir('ADMIN');
    (fixture.nativeElement.querySelector('.titulo .btn') as HTMLButtonElement).click();
    fixture.detectChanges();

    const tela = fixture.nativeElement as HTMLElement;
    (tela.querySelector('#nome') as HTMLInputElement).value = 'Carla Dias';
    (tela.querySelector('#nome') as HTMLInputElement).dispatchEvent(new Event('input'));
    (tela.querySelector('#login') as HTMLInputElement).value = 'carla';
    (tela.querySelector('#login') as HTMLInputElement).dispatchEvent(new Event('input'));
    (tela.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const chamada = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/funcionarios');
    expect(chamada.request.body).toEqual({ nome: 'Carla Dias', login: 'carla', perfil: 'GARCOM' });
    chamada.flush({ funcionario: { ...FUNCIONARIOS[1], id: 9, nome: 'Carla Dias', login: 'carla' }, senhaProvisoria: 'Abcdefghj23' });
    http.expectOne('/api/v1/funcionarios').flush(FUNCIONARIOS);
    fixture.detectChanges();

    expect(tela.querySelector('.senha-aviso code')?.textContent).toContain('Abcdefghj23');
  });
});
