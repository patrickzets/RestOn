import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { vi } from 'vitest';

import { Comprovante as DadosDoComprovante } from '../../servicos/caixa.service';
import { Comprovante } from './comprovante';

const DADOS: DadosDoComprovante = {
  codigo: 'C-00007', status: 'FECHADA', mesaNumero: 3, numeroCliente: 2, nomeCliente: 'Paulo', abertaEm: '2026-11-05T15:00:00Z', fechadaEm: '2026-11-05T16:10:00Z',
  itens: [{ produto: 'Risoto', quantidade: 2, precoUnitario: 49.9, subtotal: 99.8, observacao: 'Sem cebola' }],
  subtotal: 99.8, taxaPercentual: 10, taxaRemovida: false, taxaValor: 9.98, total: 109.78,
  pagamentos: [{ id: 1, comandaId: 7, reservaId: null, metodo: 'DINHEIRO', origem: 'CAIXA', valor: 109.78, valorRecebido: 120, troco: 10.22, status: 'CONFIRMADO', criadoEm: '2026-11-05T16:05:00Z' }],
  pago: 109.78, troco: 10.22,
};

describe('Comprovante', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Comprovante],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '7' }) } } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function abrir(dados: DadosDoComprovante = DADOS) {
    const fixture = TestBed.createComponent(Comprovante);
    fixture.detectChanges();
    http.expectOne('/api/v1/comandas/7/comprovante').flush(dados);
    fixture.detectChanges();
    return fixture;
  }

  function texto(fixture: ReturnType<typeof abrir>): string {
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/ /g, ' ');
  }

  it('mostra os itens, a taxa, o total, os pagamentos e o troco', () => {
    const t = texto(abrir());

    expect(t).toContain('Comprovante de pagamento');
    expect(t).toContain('C-00007');
    expect(t).toContain('Risoto');
    expect(t).toContain('Sem cebola');
    expect(t).toContain('Taxa de serviço (10%)');
    expect(t).toContain('R$ 109,78');
    expect(t).toContain('Dinheiro');
    expect(t).toContain('Troco devolvido');
    expect(t).toContain('R$ 10,22');
  });

  it('uma comanda ainda aberta aparece como conta parcial', () => {
    const t = texto(abrir({ ...DADOS, status: 'ABERTA', fechadaEm: null, pagamentos: [], pago: 0, troco: 0 }));

    expect(t).toContain('Conta parcial');
    expect(t).not.toContain('Fechada em');
  });

  it('o botão Imprimir abre a impressão do navegador', () => {
    const imprimir = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    const fixture = abrir();
    const botoes = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')) as HTMLButtonElement[];
    botoes.find((b) => b.textContent?.includes('Imprimir'))!.click();

    expect(imprimir).toHaveBeenCalled();
  });

  it('o botão Voltar volta para a tela anterior', () => {
    const voltar = vi.spyOn(TestBed.inject(Location), 'back').mockImplementation(() => undefined);
    const fixture = abrir();
    const botoes = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')) as HTMLButtonElement[];
    botoes.find((b) => b.textContent?.includes('Voltar'))!.click();

    expect(voltar).toHaveBeenCalled();
  });

  it('mostra o erro quando a comanda não pode ter comprovante', () => {
    const fixture = TestBed.createComponent(Comprovante);
    fixture.detectChanges();
    http.expectOne('/api/v1/comandas/7/comprovante').flush({ detail: 'Uma comanda cancelada não tem comprovante.' }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent).toContain('cancelada');
  });
});
