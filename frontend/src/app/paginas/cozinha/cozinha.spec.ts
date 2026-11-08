import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { signal } from '@angular/core';

import { AuthService, Usuario } from '../../servicos/auth.service';
import { Pedido } from '../../servicos/comandas.service';
import { Cozinha } from './cozinha';

function minutosAtras(minutos: number): string {
  return new Date(Date.now() - minutos * 60000).toISOString();
}

function pedido(id: number, extra: Partial<Pedido>): Pedido {
  return {
    id, comandaId: 1, comandaCodigo: 'C-00001', numeroCliente: 1, mesaNumero: 4, destino: 'COZINHA', status: 'RECEBIDO',
    criadoEm: minutosAtras(2), tempoPrevistoMin: 20,
    itens: [{ id: id * 10, produtoId: 1, produtoNome: 'Risoto de funghi', quantidade: 2, precoUnitario: 49, subtotal: 98, observacao: 'Sem cebola', cancelado: false, motivoCancelamento: null, comandaId: 1 }],
    ...extra,
  };
}

const PEDIDOS: Pedido[] = [
  pedido(1, { status: 'RECEBIDO' }),
  pedido(2, { status: 'EM_PREPARO', mesaNumero: 7, criadoEm: minutosAtras(40), tempoPrevistoMin: 10 }),
  pedido(3, { status: 'PRONTO', mesaNumero: 9 }),
  pedido(4, { destino: 'BAR', mesaNumero: 5, tempoPrevistoMin: 3 }),
  pedido(5, { destino: 'SEM_PREPARO', status: 'PRONTO', mesaNumero: 6 }),
];

describe('Cozinha', () => {
  let http: HttpTestingController;
  const usuario = signal<Usuario | null>(null);

  beforeEach(async () => {
    try {
      localStorage.clear();
    } catch {
      // sem localStorage nos testes: segue sem
    }
    usuario.set(null);
    await TestBed.configureTestingModule({
      imports: [Cozinha],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: { usuario } }],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function abrir(lista: Pedido[] = PEDIDOS): ComponentFixture<Cozinha> {
    const fixture = TestBed.createComponent(Cozinha);
    fixture.detectChanges();
    http.expectOne('/api/v1/cozinha/pedidos').flush(lista);
    fixture.detectChanges();
    return fixture;
  }

  function tela(fixture: ComponentFixture<Cozinha>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function botao(fixture: ComponentFixture<Cozinha>, texto: string): HTMLButtonElement {
    const botoes = Array.from(tela(fixture).querySelectorAll('button')) as HTMLButtonElement[];
    return botoes.find((b) => b.textContent?.trim().startsWith(texto))!;
  }

  it('põe na fila o que falta fazer, do mais antigo para o mais novo, e separa os prontos', () => {
    const fixture = abrir();
    const tickets = Array.from(tela(fixture).querySelectorAll('.fila .ticket')) as HTMLElement[];

    expect(tickets.length).toBe(2);
    expect(tickets[0].textContent).toContain('Mesa 7'); // chegou há 40 min
    expect(tickets[1].textContent).toContain('Mesa 4');
    expect(tela(fixture).querySelector('.prontos')?.textContent).toContain('Mesa 9');
  });

  it('mostra a quantidade, o produto e a observação de cada item', () => {
    const texto = tela(abrir()).textContent;

    expect(texto).toContain('2×');
    expect(texto).toContain('Risoto de funghi');
    expect(texto).toContain('Sem cebola');
  });

  it('produto sem preparo não aparece no painel da cozinha', () => {
    expect(tela(abrir()).textContent).not.toContain('Mesa 6');
  });

  it('o filtro troca entre cozinha e bar', () => {
    const fixture = abrir();
    botao(fixture, 'Bar').click();
    fixture.detectChanges();

    expect(tela(fixture).textContent).toContain('Mesa 5');
    expect(tela(fixture).textContent).not.toContain('Mesa 4');
  });

  it('marca como atrasado o pedido que passou do tempo previsto', () => {
    const fixture = abrir();

    expect(tela(fixture).querySelectorAll('.ticket.atrasado').length).toBe(1);
    expect(tela(fixture).querySelector('.ticket.atrasado')?.textContent).toContain('Mesa 7');
  });

  it('o único botão do ticket é Pronto, mesmo para o pedido recém-chegado', () => {
    const fixture = abrir();
    const textos = (Array.from(tela(fixture).querySelectorAll('.ticket button')) as HTMLElement[]).map((b) => b.textContent?.trim());

    expect(textos).toEqual(['Pronto', 'Pronto']);
    expect(tela(fixture).textContent).not.toContain('Iniciar preparo');
  });

  it('Pronto manda o pedido direto para pronto', () => {
    const fixture = abrir();
    (tela(fixture).querySelectorAll('.ticket')[1].querySelector('button') as HTMLButtonElement).click();

    const chamada = http.expectOne((r) => r.method === 'PATCH' && r.url === '/api/v1/pedidos/1/status');
    expect(chamada.request.body).toEqual({ status: 'PRONTO' });
    chamada.flush({ ...PEDIDOS[0], status: 'PRONTO' });
    http.expectOne('/api/v1/cozinha/pedidos').flush(PEDIDOS);
  });

  it('o pedido pronto só espera o garçom: não tem botão', () => {
    const fixture = abrir();

    expect(tela(fixture).querySelector('.prontos button')).toBeNull();
  });

  it('mostra o erro da API quando não consegue mudar o pedido', () => {
    const fixture = abrir();
    botao(fixture, 'Pronto').click();
    http.expectOne((r) => r.url.startsWith('/api/v1/pedidos/')).flush({ detail: 'Este pedido já está em preparo.' }, { status: 422, statusText: 'Unprocessable Entity' });
    http.expectOne('/api/v1/cozinha/pedidos').flush(PEDIDOS);
    fixture.detectChanges();

    expect(tela(fixture).querySelector('[role="alert"]')?.textContent).toContain('já está em preparo');
  });

  it('toca o plim só para pedido novo, não na primeira carga', () => {
    const fixture = abrir();
    const plim = vi.spyOn(fixture.componentInstance, 'tocarPlim').mockImplementation(() => {});
    expect(plim).not.toHaveBeenCalled();

    fixture.componentInstance.carregar(true);
    http.expectOne('/api/v1/cozinha/pedidos').flush(PEDIDOS);
    expect(plim).not.toHaveBeenCalled();

    fixture.componentInstance.carregar(true);
    http.expectOne('/api/v1/cozinha/pedidos').flush([...PEDIDOS, pedido(8, { mesaNumero: 2 })]);
    expect(plim).toHaveBeenCalledTimes(1);
  });

  it('não toca com o som desligado e não quebra com o áudio bloqueado', () => {
    const fixture = abrir();
    expect(() => fixture.componentInstance.tocarPlim()).not.toThrow();

    const plim = vi.spyOn(fixture.componentInstance, 'tocarPlim').mockImplementation(() => {});
    botao(fixture, 'Som ligado').click();
    fixture.componentInstance.carregar(true);
    http.expectOne('/api/v1/cozinha/pedidos').flush([...PEDIDOS, pedido(8, {})]);
    expect(plim).not.toHaveBeenCalled();
  });

  it('alternar o som guarda a preferência', () => {
    const fixture = abrir();
    botao(fixture, 'Som ligado').click();
    fixture.detectChanges();

    expect(localStorage.getItem('reston.cozinha.som')).toBe('0');
    expect(botao(fixture, 'Som desligado')).toBeTruthy();
  });

  it('lembra do som desligado ao abrir de novo', () => {
    localStorage.setItem('reston.cozinha.som', '0');
    const fixture = abrir();

    expect(botao(fixture, 'Som desligado')).toBeTruthy();
  });

  it('o pedido atrasado mostra o tempo em vermelho com "atrasado há"', () => {
    const fixture = abrir();
    const atraso = tela(fixture).querySelector('.ticket.atrasado .texto-atraso');

    expect(atraso?.textContent).toContain('atrasado há 30 min');
  });

  it('pedido recém-chegado ganha o realce de novo', () => {
    const fixture = abrir([pedido(1, { criadoEm: minutosAtras(0) })]);

    expect(tela(fixture).querySelector('.ticket.novo')).not.toBeNull();
  });

  it('o bartender vê a tela Bar, sem filtros e só com os botões de preparo', () => {
    usuario.set({ id: 1, nome: 'Bia', login: 'bia', perfil: 'BARTENDER', senhaProvisoria: false });
    const fixture = abrir([pedido(4, { destino: 'BAR', mesaNumero: 5 })]);

    expect(tela(fixture).querySelector('h1')?.textContent).toBe('Bar');
    expect(tela(fixture).querySelectorAll('.filtro:not(.som)').length).toBe(0);
    expect(tela(fixture).textContent).toContain('Mesa 5');
    expect(botao(fixture, 'Pronto')).toBeTruthy();
    expect(tela(fixture).textContent).not.toContain('Entregue');
  });

  it('avisa quando não há nenhum pedido', () => {
    const fixture = abrir([]);

    expect(tela(fixture).querySelectorAll('.vazio-fila').length).toBe(2);
  });
});
