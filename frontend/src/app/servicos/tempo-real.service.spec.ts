import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { TempoRealService } from './tempo-real.service';

// Um EventSource de mentira: guarda os ouvintes para o teste "disparar" os avisos do servidor
class EventSourceFalso {
  static ultimo: EventSourceFalso;
  ouvintes: Record<string, (() => void)[]> = {};
  fechado = false;

  constructor(public url: string) {
    EventSourceFalso.ultimo = this;
  }

  addEventListener(evento: string, ouvinte: () => void): void {
    (this.ouvintes[evento] ??= []).push(ouvinte);
  }

  disparar(evento: string): void {
    (this.ouvintes[evento] ?? []).forEach((o) => o());
  }

  close(): void {
    this.fechado = true;
  }
}

describe('TempoRealService', () => {
  beforeEach(() => vi.stubGlobal('EventSource', EventSourceFalso));
  afterEach(() => vi.unstubAllGlobals());

  it('abre a conexão do canal certo e avisa a cada mudança', () => {
    const avisos: number[] = [];
    const inscricao = TestBed.inject(TempoRealService).ouvir('pedidos').subscribe(() => avisos.push(1));

    expect(EventSourceFalso.ultimo.url).toBe('/api/v1/stream/pedidos');
    EventSourceFalso.ultimo.disparar('mudou');
    EventSourceFalso.ultimo.disparar('mudou');

    expect(avisos.length).toBe(2);
    inscricao.unsubscribe();
  });

  it('avisa também quando a conexão (re)abre, para não perder nada enquanto esteve fora', () => {
    const avisos: number[] = [];
    const inscricao = TestBed.inject(TempoRealService).ouvir('mesas').subscribe(() => avisos.push(1));

    EventSourceFalso.ultimo.disparar('conectado');

    expect(avisos.length).toBe(1);
    inscricao.unsubscribe();
  });

  it('fecha a conexão quando a tela deixa de ouvir', () => {
    const inscricao = TestBed.inject(TempoRealService).ouvir('mesas').subscribe();
    inscricao.unsubscribe();

    expect(EventSourceFalso.ultimo.fechado).toBe(true);
  });

  it('sem suporte a EventSource, não faz nada e não quebra', () => {
    vi.unstubAllGlobals();
    vi.stubGlobal('EventSource', undefined);
    const avisos: number[] = [];

    TestBed.inject(TempoRealService).ouvir('pedidos').subscribe(() => avisos.push(1));

    expect(avisos.length).toBe(0);
  });
});
