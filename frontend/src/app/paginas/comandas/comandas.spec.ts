import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService, Perfil } from '../../servicos/auth.service';
import { Comanda, Pedido } from '../../servicos/comandas.service';
import { Comandas } from './comandas';

const ITEM = { id: 100, produtoId: 1, produtoNome: 'Risoto', quantidade: 2, precoUnitario: 49, subtotal: 98, observacao: 'Sem cebola', cancelado: false, motivoCancelamento: null, comandaId: 1 };
const PEDIDO_PRONTO = { id: 30, comandaId: 1, comandaCodigo: 'C-00001', numeroCliente: 1, mesaNumero: 4, destino: 'COZINHA', status: 'PRONTO', criadoEm: '2026-11-05T15:00:00Z', tempoPrevistoMin: 10, itens: [ITEM] } as Pedido;
const PEDIDO_RECEBIDO = { ...PEDIDO_PRONTO, id: 31, status: 'RECEBIDO', itens: [{ ...ITEM, id: 101, produtoNome: 'Suco' }] } as Pedido;
const PEDIDO_EM_PREPARO = { ...PEDIDO_RECEBIDO, id: 32, status: 'EM_PREPARO' } as Pedido;
// Pedidos em andamento de todas as comandas (a comanda 1 tem 1 pronto e 2 na cozinha/bar)
const EM_ANDAMENTO = [PEDIDO_PRONTO, PEDIDO_RECEBIDO, PEDIDO_EM_PREPARO];

function comanda(id: number, extra: Partial<Comanda> = {}): Comanda {
  return {
    id, codigo: 'C-0000' + id, atendimentoId: 10, mesaId: 4, mesaNumero: 4, numeroCliente: id, nomeCliente: null, status: 'ABERTA',
    taxaPercentual: 10, taxaRemovida: false, subtotal: 98, taxaValor: 9.8, total: 107.8, pago: 0, restante: 107.8, pagamentos: null, abertaEm: new Date().toISOString(), fechadaEm: null,
    pedidos: null, ...extra,
  };
}

// A mesa 8 vem primeiro de propósito: a tela precisa ordenar por número
const LISTA = [
  comanda(2, { mesaNumero: 8, nomeCliente: 'Paulo', total: 0, subtotal: 0, taxaValor: 0 }),
  comanda(1),
  comanda(3, { numeroCliente: 3, mesaNumero: 4 }),
];
const DETALHE = comanda(1, { pedidos: [PEDIDO_RECEBIDO, PEDIDO_PRONTO] });

describe('Comandas', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Comandas],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  const listagem = (r: { method: string; url: string }) => r.method === 'GET' && r.url === '/api/v1/comandas';
  const andamento = (r: { method: string; url: string }) => r.method === 'GET' && r.url === '/api/v1/cozinha/pedidos';

  function abrir(perfil: Perfil = 'GARCOM'): ComponentFixture<Comandas> {
    TestBed.inject(AuthService).usuario.set({ id: 1, nome: 'Quem Mexe', login: 'quem', perfil, senhaProvisoria: false });
    const fixture = TestBed.createComponent(Comandas);
    fixture.detectChanges();
    http.expectOne(listagem).flush(LISTA);
    http.expectOne(andamento).flush(EM_ANDAMENTO);
    fixture.detectChanges();
    return fixture;
  }

  function tela(fixture: ComponentFixture<Comandas>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function cartaoDe(fixture: ComponentFixture<Comandas>, codigo: string): HTMLElement {
    const cartoes = Array.from(tela(fixture).querySelectorAll('.cartao')) as HTMLElement[];
    return cartoes.find((c) => c.textContent!.includes(codigo))!;
  }

  function abrirDetalhe(fixture: ComponentFixture<Comandas>, detalhe: Comanda = DETALHE): void {
    cartaoDe(fixture, 'C-00001').click();
    http.expectOne('/api/v1/comandas/1').flush(detalhe);
    fixture.detectChanges();
  }

  function botao(fixture: ComponentFixture<Comandas>, texto: string): HTMLButtonElement {
    const botoes = Array.from(tela(fixture).querySelectorAll('button')) as HTMLButtonElement[];
    return botoes.find((b) => b.textContent?.trim().startsWith(texto))!;
  }

  // Depois de uma ação, a lista, os pedidos em andamento e o detalhe são recarregados
  function recarregar(detalhe: Comanda = DETALHE): void {
    http.expectOne(listagem).flush(LISTA);
    http.expectOne(andamento).flush(EM_ANDAMENTO);
    http.expectOne('/api/v1/comandas/1').flush(detalhe);
  }

  it('agrupa as comandas por mesa, com as mesas em ordem e o tempo sentados', () => {
    const fixture = abrir();
    const mesas = Array.from(tela(fixture).querySelectorAll('.mesa')) as HTMLElement[];

    expect(mesas.length).toBe(2);
    expect(mesas[0].querySelector('h2')!.textContent).toContain('Mesa 4');
    expect(mesas[1].querySelector('h2')!.textContent).toContain('Mesa 8');
    expect(mesas[0].textContent).toContain('sentados há 0 min');
    const nomes = Array.from(mesas[0].querySelectorAll('.cartao strong')).map((e) => e.textContent);
    expect(nomes).toEqual(['Comanda 1', 'Comanda 3']);
    expect(mesas[1].querySelectorAll('.cartao').length).toBe(1);
  });

  it('mostra código e total, e nunca o nome do cliente', () => {
    const texto = tela(abrir()).textContent!.replace(/ /g, ' ');

    expect(texto).toContain('C-00001');
    expect(texto).toContain('Comanda 2');
    expect(texto).toContain('R$ 107,80');
    expect(texto).not.toContain('Paulo');
  });

  it('mostra selos de prontos, na cozinha/bar e sem pedidos', () => {
    const fixture = abrir();

    expect(cartaoDe(fixture, 'C-00001').querySelector('.selo.ok')!.textContent).toContain('1 pronto');
    expect(cartaoDe(fixture, 'C-00001').querySelector('.selo.aviso')!.textContent).toContain('2 na cozinha/bar');
    expect(cartaoDe(fixture, 'C-00002').textContent).toContain('sem pedidos');
  });

  it('busca pelo número da mesa', () => {
    const fixture = abrir();
    const busca = tela(fixture).querySelector('input[type="search"]') as HTMLInputElement;
    busca.value = '8';
    busca.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(tela(fixture).querySelectorAll('.cartao').length).toBe(1);
    expect(tela(fixture).textContent).toContain('Mesa 8');
  });

  it('trocar o filtro pede a lista da situação escolhida', () => {
    const fixture = abrir();
    botao(fixture, 'Fechadas').click();

    const chamada = http.expectOne(listagem);
    expect(chamada.request.params.get('status')).toBe('FECHADA');
    chamada.flush([]);
    fixture.detectChanges();

    expect(tela(fixture).textContent).toContain('Nenhuma comanda encontrada');
  });

  it('o detalhe mostra prontos antes da cozinha, itens, taxa e total, sem Confirmar entrega', () => {
    const fixture = abrir();
    abrirDetalhe(fixture);
    const gaveta = tela(fixture).querySelector('.gaveta') as HTMLElement;
    const texto = gaveta.textContent!.replace(/ /g, ' ');

    expect(texto).toContain('Sem cebola');
    expect(texto).toContain('Taxa de serviço (10%)');
    expect(texto).toContain('R$ 107,80');
    expect(texto).not.toContain('Confirmar entrega');
    expect(texto.indexOf('Prontos')).toBeLessThan(texto.indexOf('Na cozinha/bar'));
    expect(texto.indexOf('Risoto')).toBeLessThan(texto.indexOf('Suco'));
    expect(gaveta.querySelector('.rodape-fixo')!.textContent).toContain('Lançar pedido');
    expect(gaveta.querySelector('.rodape-fixo')!.textContent).toContain('Ver a conta');
  });

  it('pedidos entregues ficam recolhidos em Anteriores', () => {
    const fixture = abrir();
    const entregue = { ...PEDIDO_RECEBIDO, id: 33, status: 'ENTREGUE', itens: [{ ...ITEM, id: 105, produtoNome: 'Torta' }] } as Pedido;
    abrirDetalhe(fixture, comanda(1, { pedidos: [PEDIDO_PRONTO, entregue] }));
    expect(tela(fixture).querySelector('.gaveta')!.textContent).not.toContain('Torta');

    botao(fixture, 'Anteriores').click();
    fixture.detectChanges();
    expect(tela(fixture).querySelector('.gaveta')!.textContent).toContain('Torta');
  });

  describe('cancelar pedido inteiro', () => {
    const TRES_ITENS = comanda(1, {
      pedidos: [{ ...PEDIDO_PRONTO, status: 'RECEBIDO', itens: [ITEM, { ...ITEM, id: 102, produtoNome: 'Salada' }, { ...ITEM, id: 103, produtoNome: 'Já cancelado', cancelado: true }] }],
    });

    function abrirCancelamento(): ComponentFixture<Comandas> {
      const fixture = abrir();
      abrirDetalhe(fixture, TRES_ITENS);
      (tela(fixture).querySelector('.pedido-rodape .link.perigo') as HTMLButtonElement).click();
      fixture.detectChanges();
      return fixture;
    }

    function digitarMotivo(fixture: ComponentFixture<Comandas>, texto: string): void {
      const campo = tela(fixture).querySelector('input[aria-label="Motivo do cancelamento do pedido"]') as HTMLInputElement;
      campo.value = texto;
      campo.dispatchEvent(new Event('input'));
    }

    it('recusa motivo curto e não chama a API', () => {
      const fixture = abrirCancelamento();
      digitarMotivo(fixture, 'ab');
      botao(fixture, 'Cancelar pedido').click();
      fixture.detectChanges();

      http.expectNone((r) => r.method === 'POST');
      expect(tela(fixture).querySelector('[role="alert"]')?.textContent).toContain('motivo');
    });

    it('cancela todos os itens ativos, um por vez, e recarrega só no fim', () => {
      const fixture = abrirCancelamento();
      digitarMotivo(fixture, 'Cliente desistiu');
      botao(fixture, 'Cancelar pedido').click();

      const primeira = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/itens/100/cancelar');
      expect(primeira.request.body).toEqual({ motivo: 'Cliente desistiu' });
      http.expectNone(listagem);
      primeira.flush({});
      const segunda = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/itens/102/cancelar');
      expect(segunda.request.body).toEqual({ motivo: 'Cliente desistiu' });
      http.expectNone(listagem);
      segunda.flush({});
      http.expectNone((r) => r.url === '/api/v1/itens/103/cancelar');

      recarregar(TRES_ITENS);
    });

    it('mostra o erro no painel se alguma chamada falhar', () => {
      const fixture = abrirCancelamento();
      digitarMotivo(fixture, 'Cliente desistiu');
      botao(fixture, 'Cancelar pedido').click();

      http.expectOne((r) => r.url === '/api/v1/itens/100/cancelar').flush({ mensagem: 'Item já em preparo' }, { status: 409, statusText: 'Conflict' });
      fixture.detectChanges();

      expect(tela(fixture).querySelector('[role="alert"]')).not.toBeNull();
      http.expectNone(listagem);
    });
  });

  it('cancelar um item exige o motivo e só então chama a API', () => {
    const fixture = abrir();
    abrirDetalhe(fixture);

    (tela(fixture).querySelector('.item-acoes .link.perigo') as HTMLButtonElement).click();
    fixture.detectChanges();
    botao(fixture, 'Cancelar item').click();
    fixture.detectChanges();
    http.expectNone((r) => r.method === 'POST' && r.url.includes('/cancelar'));
    expect(tela(fixture).querySelector('[role="alert"]')?.textContent).toContain('motivo');

    const motivo = tela(fixture).querySelector('input[aria-label="Motivo do cancelamento"]') as HTMLInputElement;
    motivo.value = 'Cliente desistiu';
    motivo.dispatchEvent(new Event('input'));
    botao(fixture, 'Cancelar item').click();

    const chamada = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/itens/101/cancelar');
    expect(chamada.request.body).toEqual({ motivo: 'Cliente desistiu' });
    chamada.flush({ ...PEDIDO_PRONTO, status: 'CANCELADO' });
    recarregar();
  });

  it('transfere um item para outra comanda da mesma mesa', () => {
    const fixture = abrir();
    abrirDetalhe(fixture);

    (tela(fixture).querySelector('.item-acoes .link') as HTMLButtonElement).click();
    http.expectOne((r) => r.method === 'GET' && r.url === '/api/v1/comandas' && r.params.get('atendimentoId') === '10')
      .flush([comanda(1), comanda(2)]);
    fixture.detectChanges();

    const destino = tela(fixture).querySelector('select[aria-label="Comanda de destino"]') as HTMLSelectElement;
    destino.selectedIndex = 1;
    destino.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    botao(fixture, 'Transferir').click();

    const chamada = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/itens/101/transferir');
    expect(chamada.request.body).toEqual({ comandaDestinoId: 2 });
    chamada.flush({ ...ITEM, comandaId: 2 });
    recarregar();
  });

  it('o caixa só consulta: não vê botões de lançar nem cancelar', () => {
    const fixture = abrir('CAIXA');
    abrirDetalhe(fixture);
    const gaveta = tela(fixture).querySelector('.gaveta') as HTMLElement;

    expect(gaveta.textContent).not.toContain('Lançar pedido');
    expect(gaveta.textContent).not.toContain('Cancelar item');
    expect(gaveta.textContent).not.toContain('Cancelar pedido');
  });

  it('comanda sem itens pode ser cancelada, com confirmação e Voltar primeiro', () => {
    const fixture = abrir();
    cartaoDe(fixture, 'C-00002').click();
    http.expectOne('/api/v1/comandas/2').flush(comanda(2, { pedidos: [] }));
    fixture.detectChanges();

    botao(fixture, 'Cancelar comanda').click();
    fixture.detectChanges();
    http.expectNone((r) => r.method === 'POST');

    expect(tela(fixture).querySelector('[role="alertdialog"] .btn')!.textContent).toContain('Voltar');
    (tela(fixture).querySelector('.confirmar-vermelho') as HTMLButtonElement).click();
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/comandas/2/cancelar').flush(comanda(2, { status: 'CANCELADA' }));
    http.expectOne(listagem).flush(LISTA);
    http.expectOne(andamento).flush(EM_ANDAMENTO);
    http.expectOne('/api/v1/comandas/2').flush(comanda(2, { status: 'CANCELADA', pedidos: [] }));
  });
});
