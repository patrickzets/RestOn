import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Painel } from '../../servicos/relatorios.service';
import { VisaoGeral } from './visao-geral';

const PAINEL: Painel = {
  faturamentoHoje: 1234.5,
  comandasFechadasHoje: 10,
  ticketMedioHoje: 123.45,
  comandasAbertas: 3,
  mesasOcupadas: 4,
  mesasAtivas: 12,
  pedidosEmAndamento: 5,
  produtosComEstoqueBaixo: 2,
  operadorDoCaixa: 'Carla',
  vendasPorHora: [
    { hora: 20, total: 300 },
    { hora: 12, total: 600 },
  ],
  maisVendidosHoje: [{ produto: 'Pizza', categoria: 'Pratos', quantidade: 7, receita: 280 }],
};

describe('VisaoGeral', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VisaoGeral],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir(painel: Partial<Painel> = {}): ComponentFixture<VisaoGeral> {
    const fixture = TestBed.createComponent(VisaoGeral);
    fixture.detectChanges();
    http.expectOne('/api/v1/painel').flush({ ...PAINEL, ...painel });
    fixture.detectChanges();
    return fixture;
  }

  function texto(fixture: ComponentFixture<VisaoGeral>): string {
    // o Intl usa espaço não separável entre R$ e o número
    return ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');
  }

  it('mostra os valores formatados', () => {
    const t = texto(abrir());

    expect(t).toContain('R$ 1.234,50');
    expect(t).toContain('Ticket médio R$ 123,45');
    expect(t).toContain('4 de 12');
    expect(t).toContain('Pizza');
  });

  it('desenha as barras em ordem de hora, com a maior com 100%', () => {
    const colunas = Array.from((abrir().nativeElement as HTMLElement).querySelectorAll('.coluna'));

    expect(colunas.map((c) => c.querySelector('span')?.textContent)).toEqual(['12h', '20h']);
    expect(colunas[0].getAttribute('aria-label')).toContain('12h');
    expect((colunas[0].querySelector('.barra') as HTMLElement).style.height).toBe('100%');
    expect((colunas[1].querySelector('.barra') as HTMLElement).style.height).toBe('50%');
  });

  it('mostra o operador quando o caixa está aberto', () => {
    expect(texto(abrir())).toContain('Aberto por Carla');
  });

  it('mostra caixa fechado', () => {
    const t = texto(abrir({ operadorDoCaixa: null }));

    expect(t).toContain('Fechado');
    expect(t).not.toContain('Aberto por');
  });

  it('avisa do estoque abaixo do mínimo com link', () => {
    const fixture = abrir();

    expect(texto(fixture)).toContain('2 produtos abaixo do mínimo');
    expect(fixture.nativeElement.querySelector('a[href="/estoque"]')).not.toBeNull();
  });

  it('diz que o estoque está em dia', () => {
    const fixture = abrir({ produtosComEstoqueBaixo: 0 });

    expect(texto(fixture)).toContain('Tudo em dia');
    expect(fixture.nativeElement.querySelector('a[href="/estoque"]')).toBeNull();
  });

  it('mostra o estado vazio sem inventar dados', () => {
    const t = texto(abrir({ comandasFechadasHoje: 0, faturamentoHoje: 0, vendasPorHora: [], maisVendidosHoje: [] }));

    expect(t).toContain('Nenhuma venda hoje ainda.');
  });

  it('mostra o erro com role alert', () => {
    const fixture = TestBed.createComponent(VisaoGeral);
    fixture.detectChanges();
    http.expectOne('/api/v1/painel').flush({ detail: 'Sem permissão.' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    const alerta = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(alerta.textContent).toContain('Sem permissão.');
  });

  it('recarrega ao clicar em Atualizar', () => {
    const fixture = abrir();
    (fixture.nativeElement.querySelector('.titulo .btn') as HTMLButtonElement).click();

    http.expectOne('/api/v1/painel').flush({ ...PAINEL, comandasAbertas: 9 });
    fixture.detectChanges();
    expect(fixture.componentInstance.painel()?.comandasAbertas).toBe(9);
  });
});
