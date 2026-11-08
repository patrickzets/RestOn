import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ItemDeEstoque, Movimentacao } from '../../servicos/estoque.service';
import { Estoque } from './estoque';

const RISOTO: ItemDeEstoque = { produtoId: 1, nome: 'Risoto', categoriaNome: 'Pratos', ativo: true, controla: true, quantidade: 2, minimo: 5, situacao: 'BAIXO' };
const SUCO: ItemDeEstoque = { produtoId: 2, nome: 'Suco', categoriaNome: 'Bebidas', ativo: true, controla: true, quantidade: 30, minimo: 10, situacao: 'OK' };
const AGUA: ItemDeEstoque = { produtoId: 3, nome: 'Água', categoriaNome: 'Bebidas', ativo: true, controla: true, quantidade: 0, minimo: 6, situacao: 'ZERADO' };
const PUDIM: ItemDeEstoque = { produtoId: 4, nome: 'Pudim', categoriaNome: 'Sobremesas', ativo: true, controla: false, quantidade: 0, minimo: 0, situacao: null };

const HISTORICO: Movimentacao[] = [
  { id: 9, tipo: 'SAIDA_PEDIDO', variacao: -3, saldoApos: 2, motivo: 'Pedido #4 (C-00001)', funcionario: 'Ana Souza', criadoEm: '2026-11-05T12:00:00Z' },
];

describe('Estoque', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Estoque],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function abrir(lista: ItemDeEstoque[] = [RISOTO, SUCO, AGUA, PUDIM]): ComponentFixture<Estoque> {
    const fixture = TestBed.createComponent(Estoque);
    fixture.detectChanges();
    http.expectOne('/api/v1/estoque').flush(lista);
    fixture.detectChanges();
    return fixture;
  }

  function texto(fixture: ComponentFixture<Estoque>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function clicar(fixture: ComponentFixture<Estoque>, seletor: string, indice = 0): void {
    (fixture.nativeElement.querySelectorAll(seletor)[indice] as HTMLElement).click();
    fixture.detectChanges();
  }

  it('mostra os produtos controlados com o esgotado e o baixo primeiro', () => {
    const fixture = abrir();
    const linhas = Array.from(fixture.nativeElement.querySelectorAll('tbody tr b') as NodeListOf<HTMLElement>).map((b) => b.textContent);

    expect(linhas).toEqual(['Água', 'Risoto', 'Suco']);
    expect(texto(fixture)).toContain('Esgotado');
    expect(texto(fixture)).toContain('Baixo');
  });

  it('a aba Em alerta mostra só o que está abaixo do mínimo', () => {
    const fixture = abrir();
    clicar(fixture, '.aba', 1);

    const linhas = Array.from(fixture.nativeElement.querySelectorAll('tbody tr b') as NodeListOf<HTMLElement>).map((b) => b.textContent);
    expect(linhas).toEqual(['Água', 'Risoto']);
  });

  it('começa na lista sem controle quando nenhum produto é controlado', () => {
    const fixture = abrir([PUDIM]);

    expect(texto(fixture)).toContain('Pudim');
    expect(texto(fixture)).not.toContain('Nenhum produto controlado');
  });

  it('registra uma entrada e atualiza o saldo na tela', () => {
    const fixture = abrir();
    clicar(fixture, 'tbody tr.clicavel', 1);
    http.expectOne('/api/v1/estoque/1/movimentacoes').flush(HISTORICO);
    fixture.detectChanges();

    const quantidade = fixture.nativeElement.querySelector('#entrada-qtd') as HTMLInputElement;
    quantidade.value = '12';
    quantidade.dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('form button[type=submit]') as HTMLButtonElement).click();

    const entrada = http.expectOne('/api/v1/estoque/1/entradas');
    expect(entrada.request.body).toMatchObject({ quantidade: 12 });
    entrada.flush({ ...RISOTO, quantidade: 14, situacao: 'OK' });
    http.expectOne('/api/v1/estoque/1/movimentacoes').flush(HISTORICO);
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Entrada de 12 registrada.');
    expect(fixture.nativeElement.querySelector('.saldo strong').textContent).toContain('14');
  });

  it('o ajuste exige o motivo antes de enviar', () => {
    const fixture = abrir();
    clicar(fixture, 'tbody tr.clicavel', 1);
    http.expectOne('/api/v1/estoque/1/movimentacoes').flush(HISTORICO);
    fixture.detectChanges();

    (fixture.nativeElement.querySelectorAll('form button[type=submit]')[1] as HTMLButtonElement).click();
    fixture.detectChanges();

    http.expectNone('/api/v1/estoque/1/ajustes');
    expect(texto(fixture)).toContain('Explique o motivo');
  });

  it('liga o controle de um produto sem controle', () => {
    const fixture = abrir([PUDIM]);
    clicar(fixture, 'tbody tr.clicavel');

    const inicial = fixture.nativeElement.querySelector('#inicial') as HTMLInputElement;
    inicial.value = '8';
    inicial.dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('form button[type=submit]') as HTMLButtonElement).click();

    const pedido = http.expectOne('/api/v1/estoque/4/controle');
    expect(pedido.request.method).toBe('PUT');
    expect(pedido.request.body).toMatchObject({ controla: true, quantidadeInicial: 8 });
    pedido.flush({ ...PUDIM, controla: true, quantidade: 8, situacao: 'OK' });
    http.expectOne('/api/v1/estoque/4/movimentacoes').flush([]);
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Controle de estoque ligado.');
  });

  it('mostra o erro do servidor no painel', () => {
    const fixture = abrir();
    clicar(fixture, 'tbody tr.clicavel', 1);
    http.expectOne('/api/v1/estoque/1/movimentacoes').flush(HISTORICO);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('form button[type=submit]') as HTMLButtonElement).click();
    http.expectOne('/api/v1/estoque/1/entradas').flush({ detail: 'Risoto não tem controle de estoque.' }, { status: 422, statusText: 'Unprocessable' });
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Risoto não tem controle de estoque.');
  });
});
