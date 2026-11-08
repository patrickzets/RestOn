import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { AuthService, Perfil } from '../../servicos/auth.service';
import { Mesa } from '../../servicos/mesas.service';
import { Reserva } from '../../servicos/reservas.service';
import { Reservas } from './reservas';

const RESERVAS: Reserva[] = [
  { id: 1, mesaId: 1, mesaNumero: 1, nomeCliente: 'Marina Alves', telefone: '11912345678', qtdPessoas: 3, inicio: '2026-12-10T22:00:00Z', fim: '2026-12-11T00:00:00Z', status: 'ATIVA', atrasada: false, valorAntecipado: 0, criadoEm: '2026-11-05T10:00:00Z' },
  { id: 2, mesaId: 2, mesaNumero: 2, nomeCliente: 'Paulo Reis', telefone: '11988887777', qtdPessoas: 2, inicio: '2026-12-10T23:00:00Z', fim: '2026-12-11T01:00:00Z', status: 'CANCELADA', atrasada: false, valorAntecipado: 0, criadoEm: '2026-11-05T10:00:00Z' },
];

const MESA_LIVRE: Mesa = { id: 3, numero: 3, capacidade: 6, estado: 'LIVRE', ativa: true, atendimentoId: null, atendimentoAbertoEm: null, reservaProxima: null, comandasAbertas: 0, pedidosProntos: 0, setor: 'Salão', abertoPor: null };

describe('Reservas', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Reservas],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => vi.useRealTimers());

  const listagem = (r: { method: string; url: string }) => r.method === 'GET' && r.url === '/api/v1/reservas';

  function abrir(perfil: Perfil, lista: Reserva[] = RESERVAS): ComponentFixture<Reservas> {
    TestBed.inject(AuthService).usuario.set({ id: 1, nome: 'Quem Mexe', login: 'quem', perfil, senhaProvisoria: false });
    const fixture = TestBed.createComponent(Reservas);
    fixture.detectChanges();
    http.expectOne(listagem).flush(lista);
    fixture.detectChanges();
    return fixture;
  }

  function tela(fixture: ComponentFixture<Reservas>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function digitar(fixture: ComponentFixture<Reservas>, id: string, valor: string): void {
    const campo = tela(fixture).querySelector('#' + id) as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  }

  it('lista as reservas do dia com a situação de cada uma', () => {
    const texto = tela(abrir('ADMIN')).textContent;

    expect(texto).toContain('Marina Alves');
    expect(texto).toContain('Confirmada');
    expect(texto).toContain('Cancelada');
  });

  it('o gerente vê o telefone do cliente', () => {
    expect(tela(abrir('GERENTE')).textContent).toContain('(11) 91234-5678');
  });

  it('o garçom recebe a lista sem telefone', () => {
    const semTelefone = RESERVAS.map((r) => ({ ...r, telefone: null }));
    const texto = tela(abrir('GARCOM', semTelefone)).textContent;

    expect(texto).toContain('Marina Alves');
    expect(texto).not.toContain('(11) 91234-5678');
  });

  it('reserva que não compareceu aparece encerrada, sem botões de ação', () => {
    const naoCompareceu: Reserva = { ...RESERVAS[0], status: 'NAO_COMPARECEU' };
    const fixture = abrir('ADMIN', [naoCompareceu]);
    expect(tela(fixture).textContent).toContain('Não compareceu');

    (tela(fixture).querySelector('tr.clicavel') as HTMLElement).click();
    fixture.detectChanges();

    const botoes = Array.from(tela(fixture).querySelectorAll('button')).map((b) => b.textContent ?? '');
    expect(botoes.some((t) => /Cancelar|Chegou|Salvar|Registrar/i.test(t))).toBe(false);
  });

  it('reserva anonimizada mostra que o telefone foi removido', () => {
    const anonimizada: Reserva = { ...RESERVAS[0], status: 'NAO_COMPARECEU', nomeCliente: 'Cliente anonimizado', telefone: null };
    expect(tela(abrir('GERENTE', [anonimizada])).textContent).toContain('Telefone removido (LGPD)');
  });

  it('o garçom não vê a frase de telefone removido', () => {
    const anonimizada: Reserva = { ...RESERVAS[0], status: 'NAO_COMPARECEU', nomeCliente: 'Cliente anonimizado', telefone: null };
    expect(tela(abrir('GARCOM', [anonimizada])).textContent).not.toContain('Telefone removido');
  });

  it('avisa quando não há reservas no dia', () => {
    expect(tela(abrir('ADMIN', [])).textContent).toContain('Nenhuma reserva neste dia.');
  });

  it('busca as mesas livres ao informar data, hora e pessoas e envia a reserva', () => {
    vi.useFakeTimers();
    const fixture = abrir('ADMIN');
    (tela(fixture).querySelector('.titulo .btn') as HTMLButtonElement).click();
    fixture.detectChanges();

    digitar(fixture, 'nomeCliente', 'Carla Dias');
    digitar(fixture, 'telefone', '(11) 91234-5678');
    digitar(fixture, 'data', '2026-12-20');
    digitar(fixture, 'hora', '20:00');
    digitar(fixture, 'qtdPessoas', '4');
    vi.advanceTimersByTime(400);

    const inicio = new Date('2026-12-20T20:00').toISOString();
    const busca = http.expectOne((r) => r.url === '/api/v1/mesas/disponibilidade');
    expect(busca.request.params.get('inicio')).toBe(inicio);
    expect(busca.request.params.get('pessoas')).toBe('4');
    busca.flush([MESA_LIVRE]);
    fixture.detectChanges();

    const mesa = tela(fixture).querySelector('#mesaId') as HTMLSelectElement;
    expect(mesa.textContent).toContain('Mesa 3 (6 lugares)');
    mesa.value = mesa.options[1].value;
    mesa.dispatchEvent(new Event('change'));

    (tela(fixture).querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    const envio = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/reservas');
    expect(envio.request.body).toEqual({ mesaId: 3, nomeCliente: 'Carla Dias', telefone: '(11) 91234-5678', qtdPessoas: 4, inicio });
    envio.flush({ ...RESERVAS[0], id: 3 });
    http.expectOne(listagem).flush(RESERVAS);
  });

  it('não envia a reserva com os campos vazios', () => {
    const fixture = abrir('ADMIN');
    (tela(fixture).querySelector('.titulo .btn') as HTMLButtonElement).click();
    fixture.detectChanges();

    (tela(fixture).querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    http.expectNone((r) => r.method === 'POST');
    expect(tela(fixture).textContent).toContain('Informe o nome');
  });

  it('pede confirmação antes de cancelar uma reserva', () => {
    const fixture = abrir('ADMIN');
    (tela(fixture).querySelector('tr.clicavel') as HTMLElement).click();
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/mesas/disponibilidade').flush([MESA_LIVRE]);

    const botoes = () => Array.from(tela(fixture).querySelectorAll('button')) as HTMLButtonElement[];
    botoes().find((b) => b.textContent?.includes('Cancelar reserva'))!.click();
    fixture.detectChanges();
    http.expectNone('/api/v1/reservas/1/cancelar');

    botoes().find((b) => b.textContent?.includes('Cancelar reserva'))!.click();
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/reservas/1/cancelar').flush({ ...RESERVAS[0], status: 'CANCELADA' });
    http.expectOne(listagem).flush(RESERVAS);
  });
  it('o gerente registra o pagamento antecipado da reserva', () => {
    const fixture = abrir('GERENTE');
    (tela(fixture).querySelector('tr.clicavel') as HTMLElement).click();
    http.expectOne((r) => r.url === '/api/v1/mesas/disponibilidade').flush([MESA_LIVRE]);
    fixture.detectChanges();

    digitar(fixture, 'antecipado', '30');
    const botoes = Array.from(tela(fixture).querySelectorAll('button')) as HTMLButtonElement[];
    botoes.find((b) => b.textContent?.includes('Registrar pagamento'))!.click();

    const chamada = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/reservas/1/pagamento-antecipado');
    expect(chamada.request.body).toEqual({ metodo: 'PIX', valor: 30 });
    chamada.flush({ id: 9 });
    http.expectOne(listagem).flush(RESERVAS);
  });

  it('o garçom não vê o bloco de pagamento antecipado', () => {
    const fixture = abrir('GARCOM', RESERVAS.map((r) => ({ ...r, telefone: null })));
    (tela(fixture).querySelector('tr.clicavel') as HTMLElement).click();
    http.expectOne((r) => r.url === '/api/v1/mesas/disponibilidade').flush([MESA_LIVRE]);
    fixture.detectChanges();

    expect(tela(fixture).textContent).not.toContain('Pagamento antecipado');
  });

  it('a lista mostra quanto já foi pago antecipadamente', () => {
    const texto = tela(abrir('GERENTE', [{ ...RESERVAS[0], valorAntecipado: 30 }])).textContent!.replace(/\u00a0/g, ' ');

    expect(texto).toContain('Pago R$ 30,00');
  });
});
