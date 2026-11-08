import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService, Perfil } from '../../servicos/auth.service';
import { Comanda, Pedido } from '../../servicos/comandas.service';
import { Mesa } from '../../servicos/mesas.service';
import { Mesas } from './mesas';

const AGORA = new Date();
const EM_30_MIN = new Date(AGORA.getTime() + 30 * 60000).toISOString();

// O usuário dos testes tem id 1: a mesa 2 foi aberta por ele, a mesa 6 por outro garçom
const base = { ativa: true, atendimentoId: null, atendimentoAbertoEm: null, reservaProxima: null, comandasAbertas: 0, pedidosProntos: 0, setor: 'Salão', abertoPor: null };
const MESAS: Mesa[] = [
  { ...base, id: 1, numero: 1, capacidade: 4, estado: 'LIVRE' },
  { ...base, id: 2, numero: 2, capacidade: 2, estado: 'OCUPADA', atendimentoId: 10, atendimentoAbertoEm: AGORA.toISOString(), comandasAbertas: 1, pedidosProntos: 1, abertoPor: 1 },
  { ...base, id: 3, numero: 3, capacidade: 6, estado: 'AGUARDANDO_LIBERACAO' },
  { ...base, id: 4, numero: 4, capacidade: 4, estado: 'LIVRE', reservaProxima: { id: 7, nomeCliente: 'Marina', qtdPessoas: 3, inicio: EM_30_MIN } },
  { ...base, id: 5, numero: 5, capacidade: 4, estado: 'LIVRE', ativa: false },
  { ...base, id: 6, numero: 6, capacidade: 4, estado: 'OCUPADA', setor: 'Varanda', atendimentoId: 11, atendimentoAbertoEm: AGORA.toISOString(), comandasAbertas: 1, abertoPor: 99 },
];

const item = { id: 1, produtoNome: 'Suco de laranja', quantidade: 1, subtotal: 9, observacao: null, cancelado: false };
const COMANDA_1 = {
  id: 1, codigo: 'C-00001', atendimentoId: 10, numeroCliente: 1, nomeCliente: 'Carla', status: 'ABERTA',
  subtotal: 58, taxaPercentual: 10, taxaRemovida: false, taxaValor: 5.8, total: 63.8, pago: 0, restante: 63.8,
  pedidos: [
    { id: 30, comandaId: 1, destino: 'BAR', status: 'PRONTO', itens: [item] },
    { id: 31, comandaId: 1, destino: 'COZINHA', status: 'RECEBIDO', itens: [{ ...item, id: 2, produtoNome: 'Risoto', observacao: 'Sem cebola' }, { ...item, id: 3, produtoNome: 'Salada' }] },
  ],
} as unknown as Comanda;
const COMANDA_VAZIA = { ...COMANDA_1, id: 2, numeroCliente: 2, pedidos: [] } as unknown as Comanda;
const PEDIDOS_EM_ANDAMENTO = [{ id: 30, mesaNumero: 2, status: 'PRONTO' }] as unknown as Pedido[];

describe('Mesas', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Mesas],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function abrir(perfil: Perfil): ComponentFixture<Mesas> {
    TestBed.inject(AuthService).usuario.set({ id: 1, nome: 'Quem Mexe', login: 'quem', perfil, senhaProvisoria: false });
    const fixture = TestBed.createComponent(Mesas);
    fixture.detectChanges();
    http.expectOne('/api/v1/mesas').flush(MESAS);
    fixture.detectChanges();
    return fixture;
  }

  // Abrir a mesa ocupada busca as comandas, o detalhe da primeira e os pedidos prontos
  function abrirMesaOcupada(fixture: ComponentFixture<Mesas>, numero = 2, comandas: Comanda[] = [COMANDA_1]): void {
    bloco(fixture, numero).click();
    http.expectOne((r) => r.url === '/api/v1/comandas' && r.params.get('atendimentoId') === String(numero === 2 ? 10 : 11)).flush(comandas);
    http.expectOne((r) => r.method === 'GET' && r.url === '/api/v1/cozinha/pedidos').flush(numero === 2 ? PEDIDOS_EM_ANDAMENTO : []);
    http.match((r) => r.method === 'GET' && /^\/api\/v1\/comandas\/\d+$/.test(r.url)).forEach((r) => r.flush(comandas[0]));
    fixture.detectChanges();
  }

  function tela(fixture: ComponentFixture<Mesas>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function bloco(fixture: ComponentFixture<Mesas>, numero: number): HTMLButtonElement {
    const blocos = Array.from(tela(fixture).querySelectorAll('button.mt')) as HTMLButtonElement[];
    return blocos.find((b) => b.querySelector('b')?.textContent?.trim() === String(numero))!;
  }

  function botao(fixture: ComponentFixture<Mesas>, texto: string): HTMLButtonElement {
    const botoes = Array.from(tela(fixture).querySelectorAll('button')) as HTMLButtonElement[];
    return botoes.find((b) => b.textContent?.includes(texto))!;
  }

  it('agrupa as mesas por setor e mostra o tempo e o horário da reserva', () => {
    const fixture = abrir('GARCOM');
    const setores = Array.from(tela(fixture).querySelectorAll('.setor-t')).map((t) => t.textContent);

    expect(setores.length).toBe(2);
    expect(setores[0]).toContain('Salão');
    expect(setores[1]).toContain('Varanda');
    expect(bloco(fixture, 4).textContent).toMatch(/\d{2}:\d{2}/);
    expect(bloco(fixture, 2).textContent).toContain('0 min');
  });

  it('o garçom não vê mesa desativada nem o botão de cadastrar mesa', () => {
    const fixture = abrir('GARCOM');

    expect(bloco(fixture, 5)).toBeUndefined();
    expect(tela(fixture).querySelector('.titulo .btn')).toBeNull();
  });

  it('o gerente vê a mesa desativada e pode cadastrar mesas', () => {
    const fixture = abrir('GERENTE');

    expect(bloco(fixture, 5)).toBeDefined();
    expect(tela(fixture).querySelector('.titulo .btn')).not.toBeNull();
  });

  it('o filtro Ocupadas mostra só as mesas ocupadas', () => {
    const fixture = abrir('GARCOM');
    botao(fixture, 'Ocupadas').click();
    fixture.detectChanges();

    expect(tela(fixture).querySelectorAll('button.mt').length).toBe(2);
  });

  it('o filtro Minhas mostra só as mesas que o próprio garçom abriu', () => {
    const fixture = abrir('GARCOM');
    botao(fixture, 'Minhas').click();
    fixture.detectChanges();

    const numeros = Array.from(tela(fixture).querySelectorAll('button.mt b')).map((b) => b.textContent?.trim());
    expect(numeros).toEqual(['2']);
  });

  it('o filtro Com pedido pronto mostra a mesa com o selo da quantidade', () => {
    const fixture = abrir('GARCOM');
    expect(bloco(fixture, 2).classList).toContain('pronto');
    expect(bloco(fixture, 2).querySelector('.n-p')?.textContent).toContain('1');

    botao(fixture, 'Com pedido pronto').click();
    fixture.detectChanges();
    expect(tela(fixture).querySelectorAll('button.mt').length).toBe(1);
  });

  it('o filtro de setor mostra só as mesas daquele setor', () => {
    const fixture = abrir('GARCOM');
    botao(fixture, 'Varanda').click();
    fixture.detectChanges();

    const numeros = Array.from(tela(fixture).querySelectorAll('button.mt b')).map((b) => b.textContent?.trim());
    expect(numeros).toEqual(['6']);
  });

  it('digitar o número da mesa e confirmar abre a janela dela', () => {
    const fixture = abrir('GARCOM');
    const campo = tela(fixture).querySelector('#ir-numero') as HTMLInputElement;
    campo.value = '3';
    campo.dispatchEvent(new Event('input'));
    (tela(fixture).querySelector('form.ir') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(tela(fixture).querySelector('.janela')?.textContent).toContain('Mesa 3');
  });

  it('avisa quando o número digitado não existe', () => {
    const fixture = abrir('GARCOM');
    const campo = tela(fixture).querySelector('#ir-numero') as HTMLInputElement;
    campo.value = '47';
    campo.dispatchEvent(new Event('input'));
    (tela(fixture).querySelector('form.ir') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(tela(fixture).querySelector('.janela')).toBeNull();
    expect(tela(fixture).textContent).toContain('Não achei a mesa 47');
  });

  it('abre o atendimento de uma mesa livre e atualiza a lista', () => {
    const fixture = abrir('GARCOM');
    bloco(fixture, 1).click();
    fixture.detectChanges();

    botao(fixture, 'Abrir atendimento').click();
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/mesas/1/atendimentos').flush({ id: 11, mesaId: 1, mesaNumero: 1 });
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/atendimentos/11/comandas').flush({ id: 5, numeroCliente: 1 });
    http.expectOne('/api/v1/mesas').flush(MESAS);
  });

  it('na mesa com reserva chegando, o botão registra a chegada em vez de abrir atendimento', () => {
    const fixture = abrir('GARCOM');
    bloco(fixture, 4).click();
    fixture.detectChanges();

    expect(botao(fixture, 'Abrir atendimento')).toBeUndefined();
    botao(fixture, 'O cliente da reserva chegou').click();
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/reservas/7/chegada').flush({ id: 12, mesaId: 4, mesaNumero: 4 });
    http.expectOne('/api/v1/mesas').flush(MESAS);
  });

  it('a janela da mesa ocupada mostra a comanda sem o nome do cliente, com o pronto em destaque', () => {
    const fixture = abrir('GARCOM');
    abrirMesaOcupada(fixture);
    const janela = tela(fixture).querySelector('.janela') as HTMLElement;

    expect(janela.textContent).toContain('Comanda 1');
    expect(janela.textContent).not.toContain('Carla');
    expect(janela.querySelector('.para-levar')?.textContent).toContain('Pronto, levar à mesa');
    expect(janela.textContent).toContain('Obs.: Sem cebola');
    expect(janela.textContent).toContain('Lançar pedido');
  });

  it('ao abrir a mesa, o pedido que estava pronto passa a valer como entregue', () => {
    const fixture = abrir('GARCOM');
    bloco(fixture, 2).click();
    http.expectOne((r) => r.url === '/api/v1/comandas' && r.params.get('atendimentoId') === '10').flush([COMANDA_1]);
    http.expectOne((r) => r.method === 'GET' && r.url === '/api/v1/cozinha/pedidos').flush(PEDIDOS_EM_ANDAMENTO);
    http.match((r) => r.method === 'GET' && r.url === '/api/v1/comandas/1').forEach((r) => r.flush(COMANDA_1));

    const chamada = http.expectOne((r) => r.method === 'PATCH' && r.url === '/api/v1/pedidos/30/status');
    expect(chamada.request.body).toEqual({ status: 'ENTREGUE' });
  });

  it('abrir uma nova comanda pede confirmação antes', () => {
    const fixture = abrir('GARCOM');
    abrirMesaOcupada(fixture);

    botao(fixture, '+ Nova comanda').click();
    fixture.detectChanges();
    http.expectNone((r) => r.method === 'POST' && r.url === '/api/v1/atendimentos/10/comandas');
    expect(tela(fixture).textContent).toContain('Abrir a Comanda 2?');

    botao(fixture, 'Abrir comanda').click();
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/atendimentos/10/comandas').flush({ id: 6, numeroCliente: 2 });
    http.expectOne('/api/v1/mesas').flush(MESAS);
  });

  it('cancelar o pedido cancela todos os itens com o motivo e recusa motivo curto', () => {
    const fixture = abrir('GARCOM');
    abrirMesaOcupada(fixture);

    botao(fixture, 'Cancelar pedido').click();
    fixture.detectChanges();
    botao(fixture, 'Cancelar pedido').click(); // o botão vermelho da confirmação
    fixture.detectChanges();
    http.expectNone((r) => r.url.includes('/cancelar'));
    expect(tela(fixture).textContent).toContain('pelo menos 3 letras');

    const campo = tela(fixture).querySelector('input[aria-label="Motivo do cancelamento"]') as HTMLInputElement;
    campo.value = 'Cliente desistiu';
    campo.dispatchEvent(new Event('input'));
    botao(fixture, 'Cancelar pedido').click();
    const chamadas = http.match((r) => r.method === 'POST' && r.url.startsWith('/api/v1/itens/') && r.url.endsWith('/cancelar'));
    expect(chamadas.length).toBe(2);
    expect(chamadas[0].request.body).toEqual({ motivo: 'Cliente desistiu' });
    chamadas.forEach((c) => c.flush({}));
    http.expectOne('/api/v1/mesas').flush(MESAS);
  });

  it('o garçom não cancela pedido que a cozinha já começou', () => {
    const fixture = abrir('GARCOM');
    abrirMesaOcupada(fixture, 2, [{ ...COMANDA_1, pedidos: [{ id: 40, comandaId: 1, destino: 'COZINHA', status: 'EM_PREPARO', itens: [item] }] } as unknown as Comanda]);
    expect(botao(fixture, 'Cancelar pedido')).toBeUndefined();
  });

  it('cancelar a comanda vazia fica em "Mais opções" e pede confirmação', () => {
    const fixture = abrir('GARCOM');
    abrirMesaOcupada(fixture, 2, [COMANDA_VAZIA]);

    expect(botao(fixture, 'Cancelar Comanda 2')).toBeUndefined();
    botao(fixture, 'Mais opções').click();
    fixture.detectChanges();
    botao(fixture, 'Cancelar Comanda 2').click();
    fixture.detectChanges();
    http.expectNone((r) => r.url === '/api/v1/comandas/2/cancelar');

    botao(fixture, 'Cancelar comanda').click();
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/comandas/2/cancelar').flush({});
    http.expectOne('/api/v1/mesas').flush(MESAS);
  });

  it('confirma antes de encerrar o atendimento', () => {
    const fixture = abrir('GARCOM');
    abrirMesaOcupada(fixture);

    botao(fixture, 'Mais opções').click();
    fixture.detectChanges();
    botao(fixture, 'Encerrar atendimento').click();
    fixture.detectChanges();
    http.expectNone('/api/v1/atendimentos/10/encerrar');

    (Array.from(tela(fixture).querySelectorAll('.confirma button')) as HTMLButtonElement[]).find((b) => b.textContent?.includes('Encerrar atendimento'))!.click();
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/atendimentos/10/encerrar').flush({ id: 10, mesaId: 2, mesaNumero: 2 });
    http.expectOne('/api/v1/mesas').flush(MESAS);
  });

  it('o gerente cadastra a mesa com o setor', () => {
    const fixture = abrir('GERENTE');
    botao(fixture, 'Nova mesa').click();
    fixture.detectChanges();

    const setor = tela(fixture).querySelector('#setor') as HTMLInputElement;
    setor.value = 'Balcão';
    setor.dispatchEvent(new Event('input'));
    botao(fixture, 'Cadastrar mesa').click();

    const chamada = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/mesas');
    expect(chamada.request.body).toMatchObject({ numero: 7, capacidade: 4, setor: 'Balcão' });
    chamada.flush({});
    http.expectOne('/api/v1/mesas').flush(MESAS);
  });

  it('mostra o erro da API quando a ação não pode ser feita', () => {
    const fixture = abrir('GARCOM');
    bloco(fixture, 3).click();
    fixture.detectChanges();

    botao(fixture, 'Confirmar liberação').click();
    http.expectOne('/api/v1/mesas/3/liberar').flush({ detail: 'A mesa 3 não está aguardando liberação.' }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();

    expect(tela(fixture).querySelector('[role="alert"]')?.textContent).toContain('não está aguardando liberação');
  });

  it('Esc fecha a janela da mesa', () => {
    const fixture = abrir('GARCOM');
    bloco(fixture, 1).click();
    fixture.detectChanges();
    expect(tela(fixture).querySelector('.janela')).not.toBeNull();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(tela(fixture).querySelector('.janela')).toBeNull();
  });
});
