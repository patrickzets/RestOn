import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Turno } from '../../servicos/caixa.service';
import { Vendas } from '../../servicos/relatorios.service';
import { Relatorios } from './relatorios';

function formatar(data: Date): string {
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}

function diasAtras(dias: number): string {
  const data = new Date();
  data.setDate(data.getDate() - dias);
  return formatar(data);
}

const VENDAS: Vendas = {
  de: '2026-11-01',
  ate: '2026-11-03',
  resumo: { faturamento: 1500, comandas: 10, ticketMedio: 150, taxaDeServico: 120, estornado: 30, itensCancelados: 2 },
  porDia: [
    { data: '2026-11-01', total: 500, comandas: 3 },
    { data: '2026-11-02', total: 0, comandas: 0 },
    { data: '2026-11-03', total: 1000, comandas: 7 },
  ],
  porMetodo: [
    { metodo: 'PIX', total: 900, pagamentos: 6 },
    { metodo: 'CARTAO', total: 600, pagamentos: 4 },
  ],
  maisVendidos: [{ produto: 'Risoto', categoria: 'Pratos', quantidade: 12, receita: 720 }],
  porGarcom: [{ garcom: 'Ana Souza', comandas: 10, total: 1500 }],
};

const VAZIO: Vendas = {
  de: '2026-11-01',
  ate: '2026-11-03',
  resumo: { faturamento: 0, comandas: 0, ticketMedio: 0, taxaDeServico: 0, estornado: 0, itensCancelados: 0 },
  porDia: [{ data: '2026-11-01', total: 0, comandas: 0 }],
  porMetodo: [],
  maisVendidos: [],
  porGarcom: [],
};

function turno(id: number, dados: Partial<Turno>): Turno {
  return {
    id,
    operadorId: 1,
    operadorNome: 'Caio',
    abertoEm: '2026-11-01T10:00:00Z',
    fechadoEm: '2026-11-01T18:00:00Z',
    valorInicial: 100,
    valorContado: 100,
    status: 'FECHADO',
    totalPix: 0,
    totalCartao: 0,
    totalDinheiro: 0,
    totalRecebido: 0,
    qtdPagamentos: 0,
    dinheiroEsperado: 100,
    diferenca: 0,
    ...dados,
  };
}

describe('Relatorios', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Relatorios],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function abrir(vendas: Vendas = VENDAS, turnos: Turno[] = []): ComponentFixture<Relatorios> {
    const fixture = TestBed.createComponent(Relatorios);
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url === '/api/v1/relatorios/vendas');
    expect(req.request.params.keys()).toEqual([]);
    req.flush(vendas);
    http.expectOne('/api/v1/caixa/turnos').flush(turnos);
    fixture.detectChanges();
    return fixture;
  }

  function texto(fixture: ComponentFixture<Relatorios>): string {
    return ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/ /g, ' ');
  }

  function clicarTexto(fixture: ComponentFixture<Relatorios>, seletor: string, conteudo: string): void {
    const botoes = Array.from(fixture.nativeElement.querySelectorAll(seletor) as NodeListOf<HTMLElement>);
    botoes.find((b) => b.textContent?.trim() === conteudo)!.click();
    fixture.detectChanges();
  }

  function digitar(fixture: ComponentFixture<Relatorios>, id: string, valor: string): void {
    const campo = fixture.nativeElement.querySelector('#' + id) as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  }

  it('no começo pede as vendas sem parâmetros e preenche as datas devolvidas', () => {
    const fixture = abrir();
    expect((fixture.nativeElement.querySelector('#de') as HTMLInputElement).value).toBe('2026-11-01');
    expect((fixture.nativeElement.querySelector('#ate') as HTMLInputElement).value).toBe('2026-11-03');
  });

  it('mostra o resumo e as tabelas', () => {
    const t = texto(abrir());

    expect(t).toContain('R$ 1.500,00');
    expect(t).toContain('Comandas fechadas');
    expect(t).toContain('Itens cancelados');
    expect(t).toContain('PIX');
    expect(t).toContain('Cartão');
    expect(t).toContain('Risoto');
    expect(t).toContain('Ana Souza');
  });

  it('desenha uma barra para cada dia, inclusive o dia sem venda', () => {
    const fixture = abrir();
    const barras = fixture.nativeElement.querySelectorAll('.barra-valor') as NodeListOf<HTMLElement>;

    expect(barras.length).toBe(3);
    expect(barras[1].style.height).toBe('2%');
    expect(barras[2].style.height).toBe('100%');
  });

  it('o atalho 30 dias envia de e ate calculados a partir de hoje', () => {
    const fixture = abrir();
    clicarTexto(fixture, '.atalhos button', '30 dias');

    const req = http.expectOne((r) => r.url === '/api/v1/relatorios/vendas');
    expect(req.request.params.get('de')).toBe(diasAtras(29));
    expect(req.request.params.get('ate')).toBe(diasAtras(0));
    req.flush(VENDAS);
  });

  it('o atalho Hoje usa o mesmo dia nas duas datas', () => {
    const fixture = abrir();
    clicarTexto(fixture, '.atalhos button', 'Hoje');

    const req = http.expectOne((r) => r.url === '/api/v1/relatorios/vendas');
    expect(req.request.params.get('de')).toBe(diasAtras(0));
    expect(req.request.params.get('ate')).toBe(diasAtras(0));
    req.flush(VAZIO);
  });

  it('não chama a API quando a data inicial é depois da final', () => {
    const fixture = abrir();
    digitar(fixture, 'de', '2026-11-10');
    digitar(fixture, 'ate', '2026-11-05');
    clicarTexto(fixture, 'button', 'Aplicar');

    http.expectNone('/api/v1/relatorios/vendas');
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('não pode ser depois');
  });

  it('não chama a API quando o período passa de 366 dias', () => {
    const fixture = abrir();
    digitar(fixture, 'de', '2025-01-01');
    digitar(fixture, 'ate', '2026-11-05');
    clicarTexto(fixture, 'button', 'Aplicar');

    http.expectNone('/api/v1/relatorios/vendas');
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('366');
  });

  it('em um período sem vendas mostra o estado vazio em cada bloco', () => {
    const t = texto(abrir(VAZIO));

    expect(t.split('Nenhuma venda neste período.').length - 1).toBe(4);
  });

  it('mostra a mensagem da API quando ela recusa o período (422)', () => {
    const fixture = abrir();
    digitar(fixture, 'de', '2026-11-01');
    digitar(fixture, 'ate', '2026-11-05');
    clicarTexto(fixture, 'button', 'Aplicar');

    http
      .expectOne((r) => r.url === '/api/v1/relatorios/vendas')
      .flush({ detail: 'Período inválido.' }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('Período inválido.');
  });

  it('a aba Turnos mostra faltou, sobrou, confere e em aberto', () => {
    const fixture = abrir(VENDAS, [
      turno(1, { diferenca: -12.5, valorContado: 87.5, dinheiroEsperado: 100 }),
      turno(2, { diferenca: 5, valorContado: 105, dinheiroEsperado: 100 }),
      turno(3, { diferenca: 0 }),
      turno(4, { diferenca: null, valorContado: null, fechadoEm: null, status: 'ABERTO' }),
    ]);
    clicarTexto(fixture, '.aba', 'Turnos do caixa');

    const t = texto(fixture);
    expect(t).toContain('Faltou R$ 12,50');
    expect(t).toContain('Sobrou R$ 5,00');
    expect(t).toContain('Confere');
    expect(t).toContain('Em aberto');
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(4);
  });
});
