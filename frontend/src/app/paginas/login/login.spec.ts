import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let http: HttpTestingController;
  let tela: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    http = TestBed.inject(HttpTestingController);
    tela = fixture.nativeElement;
    fixture.detectChanges();
  });

  function preencher(login: string, senha: string): void {
    (tela.querySelector('#login') as HTMLInputElement).value = login;
    (tela.querySelector('#login') as HTMLInputElement).dispatchEvent(new Event('input'));
    (tela.querySelector('#senha') as HTMLInputElement).value = senha;
    (tela.querySelector('#senha') as HTMLInputElement).dispatchEvent(new Event('input'));
  }

  function enviar(): void {
    (tela.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('não envia nada com os campos vazios e mostra o que falta', () => {
    enviar();

    http.expectNone('/api/v1/auth/login');
    expect(tela.textContent).toContain('Informe o seu login.');
    expect(tela.textContent).toContain('Informe a sua senha.');
  });

  it('mostra o aviso quando o login ou a senha estão errados', () => {
    preencher('maria', 'senha-errada-1');
    enviar();

    http.expectOne('/api/v1/auth/login').flush({ detail: 'Login ou senha inválidos.' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(tela.querySelector('[role="alert"]')?.textContent).toContain('Login ou senha inválidos.');
  });

  it('avisa quando o servidor está fora do ar', () => {
    preencher('maria', 'qualquer-senha-1');
    enviar();

    http.expectOne('/api/v1/auth/login').error(new ProgressEvent('error'));
    fixture.detectChanges();

    expect(tela.querySelector('[role="alert"]')?.textContent).toContain('Não consegui falar com o servidor');
  });

  it('depois de entrar, vai para a página inicial do perfil', () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    preencher('maria', 'senha-certa-123');
    enviar();

    http.expectOne('/api/v1/auth/login').flush({ id: 1, nome: 'Maria', login: 'maria', perfil: 'GARCOM', senhaProvisoria: false });

    expect(navegar).toHaveBeenCalledWith('/mesas');
  });

  it('no primeiro acesso, vai direto para a troca de senha', () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    preencher('novo', 'provisoria-123');
    enviar();

    http.expectOne('/api/v1/auth/login').flush({ id: 5, nome: 'Novo', login: 'novo', perfil: 'CAIXA', senhaProvisoria: true });

    expect(navegar).toHaveBeenCalledWith('/trocar-senha');
  });

  it('o botão Mostrar revela a senha', () => {
    const campo = tela.querySelector('#senha') as HTMLInputElement;
    expect(campo.type).toBe('password');

    (tela.querySelector('.mostrar') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(campo.type).toBe('text');
  });


  it('avisa quando o Caps Lock está ligado na senha', () => {
    const senha = tela.querySelector('#senha') as HTMLInputElement;
    const evento = new KeyboardEvent('keydown', { key: 'A' });
    vi.spyOn(evento, 'getModifierState').mockReturnValue(true);
    senha.dispatchEvent(evento);
    fixture.detectChanges();

    expect(tela.textContent).toContain('Caps Lock ligado');

    senha.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(tela.textContent).not.toContain('Caps Lock ligado');
  });

  it('lembra só o login, nunca a senha, quando a pessoa marca a opção', () => {
    localStorage.removeItem('reston.login');
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    preencher('maria', 'senha-boa-123');
    (tela.querySelector('.lembrar input') as HTMLInputElement).click();
    enviar();
    http.expectOne('/api/v1/auth/login').flush({ id: 1, nome: 'Maria', login: 'maria', perfil: 'GARCOM', senhaProvisoria: false });

    expect(localStorage.getItem('reston.login')).toBe('maria');
    expect(JSON.stringify(Object.entries(localStorage))).not.toContain('senha-boa-123');
    localStorage.removeItem('reston.login');
  });
});
