import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService, Perfil } from '../../servicos/auth.service';
import { Turno } from '../../servicos/caixa.service';
import { Comanda, Pedido } from '../../servicos/comandas.service';
import { Caixa } from './caixa';

const TURNO = {
  id: 7, operadorId: 3, operadorNome: 'Caio Lima', abertoEm: '2026-11-05T14:00:00Z', fechadoEm: null, valorInicial: 100, valorContado: null,
  status: 'ABERTO', totalPix: 30, totalCartao: 20, totalDinheiro: 10, totalRecebido: 60, qtdPagamentos: 3, dinheiroEsperado: 110, diferenca: null,
} as Turno;

function pedido(id: number, status: Pedido['status']): Pedido {
  return {
    id, comandaId: 1, comandaCodigo: 'C-00001', numeroCliente: 1, mesaNumero: 4, destino: 'COZINHA', status, criadoEm: '2026-11-05T15:00:00Z', tempoPrevistoMin: 10,
    itens: [{ id: id * 10, produtoId: 1, produtoNome: 'Risoto', quantidade: 2, precoUnitario: 49.9, subtotal: 99.8, observacao: null, cancelado: false, motivoCancelamento: null, comandaId: 1 }],
  };
}

function comanda(id: number, extra: Partial<Comanda> = {}): Comanda {
  return {
    id, codigo: 'C-0000' + id, atendimentoId: 10, mesaId: 4, mesaNumero: 4, numeroCliente: id, nomeCliente: null, status: 'ABERTA', taxaPercentual: 10,
    taxaRemovida: false, subtotal: 99.8, taxaValor: 9.98, total: 109.78, pago: 0, restante: 109.78, abertaEm: '2026-11-05T15:00:00Z', fechadaEm: null,
    pedidos: [pedido(30, 'ENTREGUE')], pagamentos: [], ...extra,
  };
}

let DETALHE_DA_LISTA: Comanda;
const LISTA = [comanda(1), comanda(2, { total: 0, subtotal: 0, taxaValor: 0, restante: 0 })];

describe('Caixa', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    DETALHE_DA_LISTA = comanda(1);
    await TestBed.configureTestingModule({
      imports: [Caixa],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function abrir(perfil: Perfil = 'CAIXA', turno: Turno | null = TURNO): ComponentFixture<Caixa> {
    TestBed.inject(AuthService).usuario.set({ id: 3, nome: 'Quem Mexe', login: 'quem', perfil, senhaProvisoria: false });
    const fixture = TestBed.createComponent(Caixa);
    fixture.detectChanges();
    http.expectOne('/api/v1/caixa/turno-atual').flush(turno);
    http.expectOne((r) => r.method === 'GET' && r.url === '/api/v1/comandas').flush(LISTA);
    // A tela busca o detalhe das comandas com conta para avisar de pedido na cozinha
    http.expectOne('/api/v1/comandas/1').flush(DETALHE_DA_LISTA);
    fixture.detectChanges();
    return fixture;
  }

  function tela(fixture: ComponentFixture<Caixa>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function texto(fixture: ComponentFixture<Caixa>): string {
    return tela(fixture).textContent!.replace(/ /g, ' ');
  }

  function botao(fixture: ComponentFixture<Caixa>, rotulo: string): HTMLButtonElement {
    const botoes = Array.from(tela(fixture).querySelectorAll('button')) as HTMLButtonElement[];
    return botoes.find((b) => b.textContent?.trim().startsWith(rotulo))!;
  }

  function digitar(fixture: ComponentFixture<Caixa>, id: string, valor: string): void {
    const campo = tela(fixture).querySelector('#' + id) as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  }

  // Abre a comanda 1 e responde as chamadas do detalhe
  function abrirDetalhe(fixture: ComponentFixture<Caixa>, detalhe: Comanda = comanda(1)): void {
    (tela(fixture).querySelector('button.cartao') as HTMLElement).click();
    http.expectOne('/api/v1/comandas/1').flush(detalhe);
    fixture.detectChanges();
  }

  // Depois de uma ação a tela recarrega o turno, a lista e a comanda
  function recarregar(detalhe: Comanda = comanda(1)): void {
    http.expectOne('/api/v1/caixa/turno-atual').flush(TURNO);
    http.expectOne((r) => r.method === 'GET' && r.url === '/api/v1/comandas').flush(LISTA);
    http.expectOne('/api/v1/comandas/1').flush(detalhe);
  }

  function metodo(fixture: ComponentFixture<Caixa>, nome: string): void {
    const botoes = Array.from(tela(fixture).querySelectorAll('.metodo')) as HTMLElement[];
    botoes.find((b) => b.textContent?.trim() === nome)!.click();
    fixture.detectChanges();
  }

  it('sem caixa aberto, mostra o aviso e o formulário para abrir', () => {
    const fixture = abrir('CAIXA', null);

    expect(texto(fixture)).toContain('Caixa fechado');
    expect(tela(fixture).querySelector('#inicial')).not.toBeNull();
  });

  it('abre o caixa com o valor inicial', () => {
    const fixture = abrir('CAIXA', null);
    digitar(fixture, 'inicial', '100');
    botao(fixture, 'Abrir caixa').click();

    const chamada = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/caixa/turnos');
    expect(chamada.request.body).toEqual({ valorInicial: 100 });
    chamada.flush(TURNO);
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Caixa aberto');
  });

  it('mostra o erro da API quando já existe um caixa aberto', () => {
    const fixture = abrir('CAIXA', null);
    botao(fixture, 'Abrir caixa').click();
    http.expectOne('/api/v1/caixa/turnos').flush({ detail: 'Já existe um caixa aberto.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(tela(fixture).querySelector('[role="alert"]')?.textContent).toContain('Já existe um caixa aberto');
  });

  it('com caixa aberto, a barra mostra o recebido e o dinheiro esperado', () => {
    const t = texto(abrir());

    expect(t).toContain('Caixa aberto');
    expect(t).toContain('Caio Lima');
    expect(t).toContain('R$ 60,00');
    expect(t).toContain('R$ 110,00');
  });

  it('fecha o caixa com o dinheiro contado e mostra se bateu', () => {
    const fixture = abrir();
    botao(fixture, 'Fechar caixa').click();
    fixture.detectChanges();
    digitar(fixture, 'contado', '109');
    botao(fixture, 'Fechar o caixa').click();

    const chamada = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/caixa/turnos/7/fechar');
    expect(chamada.request.body).toEqual({ valorContado: 109 });
    chamada.flush({ ...TURNO, status: 'FECHADO', valorContado: 109, diferenca: -1 });
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Faltou R$ 1,00');
    expect(texto(fixture)).toContain('Caixa fechado');
  });

  it('lista cartões por mesa com o que falta pagar, sem nome de cliente', () => {
    const t = texto(abrir());

    expect(t).toContain('Mesa 4');
    expect(t).toContain('Comanda 1');
    expect(t).toContain('R$ 109,78');
    expect(t).toContain('Sem itens');
    expect(t).not.toContain('Cliente 1');
  });

  it('o cartão avisa de pedido ainda na cozinha', () => {
    DETALHE_DA_LISTA = comanda(1, { pedidos: [pedido(31, 'EM_PREPARO')] });
    const t = texto(abrir());

    expect(t).toContain('1 pedido ainda na cozinha/bar');
  });

  it('comanda paga mas não fechada ganha o selo de pronta para fechar', () => {
    TestBed.inject(AuthService).usuario.set({ id: 3, nome: 'Quem', login: 'quem', perfil: 'CAIXA', senhaProvisoria: false });
    const fixture = TestBed.createComponent(Caixa);
    fixture.detectChanges();
    http.expectOne('/api/v1/caixa/turno-atual').flush(TURNO);
    http.expectOne((r) => r.method === 'GET' && r.url === '/api/v1/comandas').flush([comanda(1, { pago: 109.78, restante: 0 })]);
    http.expectOne('/api/v1/comandas/1').flush(comanda(1));
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Paga, pronta para fechar');
  });

  it('abre a cobrança já sugerindo o valor que falta', () => {
    const fixture = abrir();
    abrirDetalhe(fixture);

    expect((tela(fixture).querySelector('#valor') as HTMLInputElement).value).toBe('109.78');
  });

  it('recebe um pagamento em PIX', () => {
    const fixture = abrir();
    abrirDetalhe(fixture);
    digitar(fixture, 'valor', '50');
    botao(fixture, 'Receber').click();

    const chamada = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/comandas/1/pagamentos');
    expect(chamada.request.body).toEqual({ metodo: 'PIX', valor: 50 });
    chamada.flush({ id: 1, metodo: 'PIX', valor: 50 });
    recarregar(comanda(1, { pago: 50, restante: 59.78 }));
  });

  it('em dinheiro, mostra o troco e envia o valor entregue', () => {
    const fixture = abrir();
    abrirDetalhe(fixture);
    metodo(fixture, 'Dinheiro');

    digitar(fixture, 'valor', '100');
    fixture.detectChanges();
    // "Outro valor" mostra o campo livre
    botao(fixture, 'Outro valor').click();
    fixture.detectChanges();
    digitar(fixture, 'recebido', '120');
    fixture.detectChanges();
    expect(texto(fixture)).toContain('Troco: R$ 20,00');

    botao(fixture, 'Receber').click();
    const chamada = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/comandas/1/pagamentos');
    expect(chamada.request.body).toEqual({ metodo: 'DINHEIRO', valor: 100, valorRecebido: 120 });
    chamada.flush({ id: 2 });
    recarregar();
  });

  it('em dinheiro, o botão rápido de nota calcula o troco', () => {
    const fixture = abrir();
    abrirDetalhe(fixture);
    metodo(fixture, 'Dinheiro');

    botao(fixture, 'R$ 150,00').click();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('Troco: R$ 40,22');
  });

  it('sem caixa aberto, não deixa receber e explica o motivo', () => {
    const fixture = abrir('CAIXA', null);
    abrirDetalhe(fixture);

    expect(texto(fixture)).toContain('Abra o caixa para receber pagamentos.');
    expect(tela(fixture).querySelector('#valor')).toBeNull();
  });

  it('a janela de cobrança é um dialog e fecha com Esc', () => {
    const fixture = abrir();
    abrirDetalhe(fixture);

    const janela = tela(fixture).querySelector('[role="dialog"]') as HTMLElement;
    expect(janela.getAttribute('aria-modal')).toBe('true');
    expect(texto(fixture)).toContain('Mesa 4 · Comanda 1');

    janela.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(tela(fixture).querySelector('[role="dialog"]')).toBeNull();
  });

  it('o passo atual acompanha o estado: pagamento enquanto falta, fechar quando pago', () => {
    const fixture = abrir();
    abrirDetalhe(fixture);
    expect(tela(fixture).querySelector('.passos .atual')?.textContent).toContain('Pagamento');

    abrirDetalhe(fixture, comanda(1, { pago: 109.78, restante: 0 }));
    expect(tela(fixture).querySelector('.passos .atual')?.textContent).toContain('Fechar');
  });

  it('o botão de fechar só habilita com tudo pago e entregue', () => {
    const fixture = abrir();
    abrirDetalhe(fixture, comanda(1, { pago: 50, restante: 59.78 }));
    expect(botao(fixture, 'Fechar comanda')).toBeUndefined();
    expect(texto(fixture)).toContain('Receba o que falta');
  });

  it('avisa quando há pedido que ainda não foi entregue e não deixa fechar', () => {
    const fixture = abrir();
    abrirDetalhe(fixture, comanda(1, { pago: 109.78, restante: 0, pedidos: [pedido(31, 'EM_PREPARO')] }));

    expect(texto(fixture)).toContain('na cozinha/bar que não ficou pronto');
    expect(botao(fixture, 'Fechar comanda').disabled).toBe(true);
  });

  it('pedido PRONTO já conta como entregue e não segura o fechamento', () => {
    const fixture = abrir();
    abrirDetalhe(fixture, comanda(1, { pago: 109.78, restante: 0, pedidos: [pedido(31, 'PRONTO')] }));

    expect(texto(fixture)).not.toContain('na cozinha/bar que não ficou pronto');
    expect(botao(fixture, 'Fechar comanda').disabled).toBe(false);
  });

  it('fecha a comanda paga, depois de confirmar', () => {
    const fixture = abrir();
    abrirDetalhe(fixture, comanda(1, { pago: 109.78, restante: 0 }));

    botao(fixture, 'Fechar comanda').click();
    fixture.detectChanges();
    http.expectNone((r) => r.url.endsWith('/fechar'));

    (tela(fixture).querySelector('.confirmacao button.pri') as HTMLButtonElement).click();
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/comandas/1/fechar').flush(comanda(1, { status: 'FECHADA' }));
    recarregar(comanda(1, { status: 'FECHADA', pago: 109.78, restante: 0 }));
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Ver comprovante');
  });

  it('o gerente estorna o pagamento, e só com motivo', () => {
    const pagamento = { id: 5, comandaId: 1, reservaId: null, metodo: 'PIX', origem: 'CAIXA', valor: 50, valorRecebido: 50, troco: 0, status: 'CONFIRMADO', criadoEm: '2026-11-05T15:30:00Z' } as const;
    const gerente = abrir('GERENTE');
    abrirDetalhe(gerente, comanda(1, { pago: 50, restante: 59.78, pagamentos: [pagamento] }));

    expect(texto(gerente)).toContain('Mais opções');
    (tela(gerente).querySelector('.mais .link.perigo') as HTMLButtonElement).click();
    gerente.detectChanges();

    botao(gerente, 'Estornar').click();
    gerente.detectChanges();
    http.expectNone((r) => r.url.includes('/estornar'));
    expect(tela(gerente).querySelector('[role="alert"]')?.textContent).toContain('motivo');

    const campo = tela(gerente).querySelector('input[aria-label="Motivo do estorno"]') as HTMLInputElement;
    campo.value = 'Valor digitado errado';
    campo.dispatchEvent(new Event('input'));
    botao(gerente, 'Estornar').click();

    const chamada = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/pagamentos/5/estornar');
    expect(chamada.request.body).toEqual({ motivo: 'Valor digitado errado' });
    chamada.flush({ ...pagamento, status: 'ESTORNADO' });
    recarregar();
  });

  it('o caixa não estorna nem mexe na taxa', () => {
    const pagamento = { id: 5, comandaId: 1, reservaId: null, metodo: 'PIX', origem: 'CAIXA', valor: 50, valorRecebido: 50, troco: 0, status: 'CONFIRMADO', criadoEm: '2026-11-05T15:30:00Z' } as const;
    const fixture = abrir('CAIXA');
    abrirDetalhe(fixture, comanda(1, { pago: 50, restante: 59.78, pagamentos: [pagamento] }));

    expect(texto(fixture)).not.toContain('Mais opções');
    expect(texto(fixture)).not.toContain('Estornar');
    expect(texto(fixture)).not.toContain('Remover a taxa de serviço');
  });

  it('o gerente remove a taxa informando o motivo', () => {
    const fixture = abrir('GERENTE');
    abrirDetalhe(fixture);

    botao(fixture, 'Remover a taxa de serviço').click();
    fixture.detectChanges();
    const motivo = tela(fixture).querySelector('input[aria-label="Motivo para remover a taxa"]') as HTMLInputElement;
    motivo.value = 'Cortesia da casa';
    motivo.dispatchEvent(new Event('input'));
    botao(fixture, 'Remover a taxa').click();

    const chamada = http.expectOne((r) => r.method === 'PATCH' && r.url === '/api/v1/comandas/1/taxa');
    expect(chamada.request.body).toEqual({ removida: true, motivo: 'Cortesia da casa' });
    chamada.flush(comanda(1, { taxaRemovida: true, taxaValor: 0, total: 99.8, restante: 99.8 }));
    recarregar(comanda(1, { taxaRemovida: true, taxaValor: 0, total: 99.8, restante: 99.8 }));
  });
});
