import { Directive, ElementRef, HostListener, OnDestroy, OnInit, inject, output } from '@angular/core';

// Comportamento de teclado do painel de detalhe (a "gaveta"):
// Esc fecha, o foco entra no painel e volta para onde estava, e em tela estreita
// (quando o painel cobre a tela toda) o Tab fica preso dentro dele.
@Directive({
  selector: '[appGaveta]',
  host: { tabindex: '-1' },
})
export class GavetaAcessivel implements OnInit, OnDestroy {
  fechar = output<void>();

  private elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  private origem = document.activeElement as HTMLElement | null;

  ngOnInit(): void {
    if (this.telaEstreita()) setTimeout(() => this.elemento.nativeElement.focus());
  }

  ngOnDestroy(): void {
    const origem = this.origem;
    setTimeout(() => origem?.isConnected && origem.focus());
  }

  @HostListener('document:keydown.escape')
  aoApertarEsc(): void {
    this.fechar.emit();
  }

  @HostListener('keydown', ['$event'])
  prenderFoco(evento: KeyboardEvent): void {
    if (evento.key !== 'Tab' || !this.telaEstreita()) return;
    const gaveta = this.elemento.nativeElement;
    const focaveis = Array.from(
      gaveta.querySelectorAll<HTMLElement>('a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), summary'),
    );
    if (focaveis.length === 0) return;
    const primeiro = focaveis[0];
    const ultimo = focaveis[focaveis.length - 1];
    const ativo = document.activeElement;
    if (evento.shiftKey && (ativo === primeiro || ativo === gaveta)) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && ativo === ultimo) {
      evento.preventDefault();
      primeiro.focus();
    }
  }

  // Mesmo limite do CSS (.gaveta vira tela cheia até 1000px)
  private telaEstreita(): boolean {
    return window.innerWidth <= 1000;
  }
}
