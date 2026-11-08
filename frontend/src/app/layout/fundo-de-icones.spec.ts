import { TestBed } from '@angular/core/testing';

import { FundoDeIcones } from './fundo-de-icones';

describe('FundoDeIcones', () => {
  function montar(): HTMLElement {
    const fixture = TestBed.createComponent(FundoDeIcones);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('espalha vários ícones e todos apontam para um símbolo que existe', () => {
    const tela = montar();
    const usos = Array.from(tela.querySelectorAll('.ico use'));

    expect(usos.length).toBeGreaterThan(40);
    for (const uso of usos) {
      const id = (uso.getAttribute('href') ?? '').replace('#', '');
      expect(tela.querySelector(`symbol[id="${id}"]`)).not.toBeNull();
    }
  });

  it('desenha sempre a mesma coisa para o mesmo tamanho de tela', () => {
    const primeiro = Array.from(montar().querySelectorAll('.ico')).map((el) => el.getAttribute('style'));
    const segundo = Array.from(montar().querySelectorAll('.ico')).map((el) => el.getAttribute('style'));

    expect(segundo).toEqual(primeiro);
  });

  it('fica escondido para leitores de tela', () => {
    expect(montar().querySelector('.sprite')?.getAttribute('aria-hidden')).toBe('true');
  });
});
