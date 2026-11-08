import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { Categoria, Produto } from '../../servicos/cardapio.service';
import { Comanda } from '../../servicos/comandas.service';
import { PedidoNovo } from './pedido-novo';

const COMANDA = { id: 5, codigo: 'C-00005', mesaNumero: 7, numeroCliente: 2, nomeCliente: 'Paulo', status: 'ABERTA' } as Comanda;
const CATEGORIAS: Categoria[] = [
  { id: 1, nome: 'Pratos', ordem: 0, qtdProdutos: 2 },
  { id: 2, nome: 'Bebidas', ordem: 1, qtdProdutos: 1 },
  { id: 3, nome: 'Vazia', ordem: 2, qtdProdutos: 0 },
];

function produto(id: number, extra: Partial<Produto>): Produto {
  return { id, categoriaId: 1, categoriaNome: 'Pratos', nome: 'Risoto', descricao: 'Arroz cremoso', preco: 49.9, imagem: null, tempoPreparoMin: 18, destinoPreparo: 'COZINHA', disponivel: true, ativo: true, controlaEstoque: false, estoqueAtual: 0, estoqueMinimo: 0, ...extra };
}

const PRODUTOS = [
  produto(1, {}),
  produto(2, { categoriaId: 2, categoriaNome: 'Bebidas', nome: 'Suco', preco: 8, destinoPreparo: 'BAR' }),
  produto(3, { nome: 'Moqueca', disponivel: false }),
  produto(4, { nome: 'Antigo', ativo: false }),
];

describe('PedidoNovo', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PedidoNovo],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '5' }) } } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function abrir(produtos: Produto[] = PRODUTOS): ComponentFixture<PedidoNovo> {
    const fixture = TestBed.createComponent(PedidoNovo);
    fixture.detectChanges();
    http.expectOne('/api/v1/comandas/5').flush(COMANDA);
    http.expectOne('/api/v1/produtos').flush(produtos);
    http.expectOne('/api/v1/categorias').flush(CATEGORIAS);
    fixture.detectChanges();
    return fixture;
  }

  function tela(fixture: ComponentFixture<PedidoNovo>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function botao(fixture: ComponentFixture<PedidoNovo>, texto: string): HTMLButtonElement {
    const botoes = Array.from(tela(fixture).querySelectorAll('button')) as HTMLButtonElement[];
    return botoes.find((b) => b.textContent?.trim().startsWith(texto))!;
  }

  function adicionar(fixture: ComponentFixture<PedidoNovo>, produtoNome: string): void {
    (tela(fixture).querySelector(`button[aria-label="Adicionar ${produtoNome}"]`) as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  it('mostra a mesa e o cliente e só os produtos que dá para vender', () => {
    const fixture = abrir();
    const texto = tela(fixture).textContent!;

    expect(texto).toContain('Mesa 7 · Comanda 2');
    expect(texto).toContain('Risoto');
    expect(texto).toContain('Suco');
    expect(texto).not.toContain('Moqueca');
    expect(texto).not.toContain('Antigo');
  });

  it('não mostra categoria sem produto à venda', () => {
    const chips = Array.from(tela(abrir()).querySelectorAll('.chips .chip')).map((c) => c.textContent?.trim());

    expect(chips).toEqual(['Todos', 'Pratos', 'Bebidas']);
  });

  it('o botão mais adiciona e a barra de baixo soma os itens e o total', () => {
    const fixture = abrir();
    adicionar(fixture, 'Risoto');
    adicionar(fixture, 'Risoto');
    adicionar(fixture, 'Suco');

    const barra = tela(fixture).querySelector('.barra-inferior')!.textContent!.replace(/ /g, ' ');
    expect(barra).toContain('3 itens');
    expect(barra).toContain('R$ 107,80');
  });

  it('a revisão mostra para onde cada parte do pedido vai', () => {
    const fixture = abrir();
    adicionar(fixture, 'Risoto');
    adicionar(fixture, 'Suco');
    botao(fixture, 'Ver pedido').click();
    fixture.detectChanges();

    expect(tela(fixture).textContent).toContain('Será enviado em 2 pedidos: Cozinha, Bar');
  });

  it('as observações rápidas entram no item e podem ser tiradas', () => {
    const fixture = abrir();
    adicionar(fixture, 'Risoto');
    botao(fixture, 'Ver pedido').click();
    fixture.detectChanges();

    botao(fixture, 'Sem cebola').click();
    botao(fixture, 'Ao ponto').click();
    fixture.detectChanges();
    const campo = tela(fixture).querySelector('input[aria-label="Observação de Risoto"]') as HTMLInputElement;
    expect(campo.value).toBe('Sem cebola, Ao ponto');

    botao(fixture, 'Sem cebola').click();
    fixture.detectChanges();
    expect(campo.value).toBe('Ao ponto');
  });

  it('as sugestões de observação seguem o destino: prato da cozinha, bebida do bar', () => {
    const fixture = abrir();
    adicionar(fixture, 'Suco');
    botao(fixture, 'Ver pedido').click();
    fixture.detectChanges();

    expect(botao(fixture, 'Sem gelo')).toBeTruthy();
    expect(botao(fixture, 'Sem cebola')).toBeUndefined();
  });

  it('envia o pedido e volta para as mesas', () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = abrir();
    adicionar(fixture, 'Risoto');
    adicionar(fixture, 'Risoto');
    botao(fixture, 'Ver pedido').click();
    fixture.detectChanges();
    botao(fixture, 'Sem cebola').click();
    botao(fixture, 'Enviar pedido').click();

    const chamada = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/comandas/5/pedidos');
    expect(chamada.request.body).toEqual({ itens: [{ produtoId: 1, quantidade: 2, observacao: 'Sem cebola' }] });
    chamada.flush([]);

    expect(navegar).toHaveBeenCalledWith('/mesas');
  });

  it('mostra o erro da API e deixa o garçom tentar de novo', () => {
    const fixture = abrir();
    adicionar(fixture, 'Risoto');
    botao(fixture, 'Ver pedido').click();
    fixture.detectChanges();
    botao(fixture, 'Enviar pedido').click();

    http.expectOne('/api/v1/comandas/5/pedidos').flush({ detail: 'Risoto não está disponível no momento.' }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();

    expect(tela(fixture).querySelector('[role="alert"]')?.textContent).toContain('não está disponível');
    expect(botao(fixture, 'Enviar pedido').disabled).toBe(false);
  });

  it('o botão menos tira o item e remover leva de volta à escolha quando o pedido esvazia', () => {
    const fixture = abrir();
    adicionar(fixture, 'Risoto');
    botao(fixture, 'Ver pedido').click();
    fixture.detectChanges();

    botao(fixture, 'Remover').click();
    fixture.detectChanges();

    expect(tela(fixture).textContent).not.toContain('Revisar e enviar');
    expect(tela(fixture).querySelector('.barra-inferior')).toBeNull();
  });

  it('não deixa pedir mais do que o estoque e avisa quando está acabando', () => {
    const fixture = abrir([produto(1, { controlaEstoque: true, estoqueAtual: 2, estoqueMinimo: 5 }), produto(2, { nome: 'Suco', controlaEstoque: true, estoqueAtual: 0, estoqueMinimo: 3 })]);
    const texto = tela(fixture).textContent!;
    expect(texto).toContain('Restam 2');
    expect(texto).toContain('Esgotado');

    adicionar(fixture, 'Risoto');
    adicionar(fixture, 'Risoto');
    adicionar(fixture, 'Risoto');

    expect(tela(fixture).querySelector('.quantidade')!.textContent).toContain('2');
    expect((tela(fixture).querySelector('button[aria-label="Adicionar Risoto"]') as HTMLButtonElement).disabled).toBe(true);
    expect((tela(fixture).querySelector('button[aria-label="Adicionar Suco"]') as HTMLButtonElement).disabled).toBe(true);
  });
});
