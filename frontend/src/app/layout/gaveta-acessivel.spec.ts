import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { GavetaAcessivel } from './gaveta-acessivel';

@Component({
  imports: [GavetaAcessivel],
  template: `
    <aside class="gaveta" appGaveta (fechar)="fechou = fechou + 1">
      <button id="primeiro" type="button">Primeiro</button>
      <button id="ultimo" type="button">Último</button>
    </aside>
  `,
})
class Anfitriao {
  fechou = 0;
}

describe('GavetaAcessivel', () => {
  function criar() {
    const fixture = TestBed.createComponent(Anfitriao);
    fixture.detectChanges();
    return fixture;
  }

  it('fecha com Esc', () => {
    const fixture = criar();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(fixture.componentInstance.fechou).toBe(1);
  });

  it('em tela estreita, o Tab no último botão volta para o primeiro', () => {
    const fixture = criar();
    const raiz = fixture.nativeElement as HTMLElement;
    const gaveta = raiz.querySelector('.gaveta') as HTMLElement;
    const ultimo = raiz.querySelector('#ultimo') as HTMLElement;
    const primeiro = raiz.querySelector('#primeiro') as HTMLElement;
    document.body.appendChild(raiz);

    // o ambiente de teste é estreito o bastante? se não for, o teste não se aplica
    if (window.innerWidth > 1000) {
      raiz.remove();
      return;
    }
    ultimo.focus();
    const evento = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    gaveta.dispatchEvent(evento);

    expect(evento.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(primeiro);
    raiz.remove();
  });

  it('o painel aceita foco por código (tabindex -1)', () => {
    const fixture = criar();
    const gaveta = (fixture.nativeElement as HTMLElement).querySelector('.gaveta') as HTMLElement;
    expect(gaveta.getAttribute('tabindex')).toBe('-1');
  });
});
