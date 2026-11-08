import { AfterViewInit, Component, ElementRef, OnDestroy, computed, inject, signal } from '@angular/core';

const NOMES = ['chapeu', 'panela', 'cenoura', 'maca', 'cereja', 'citrico', 'uva', 'banana', 'salada', 'sopa', 'peixe', 'carne', 'croissant', 'ovo', 'bolo', 'sorvete', 'vinho', 'drinque', 'trigo', 'talheres', 'abacate', 'pimenta', 'cogumelo', 'queijo', 'pao', 'sal', 'tomate'];

// Colunas e linhas por pixel: quanto menor, mais perto ficam os ícones
const LARGURA_DA_CELULA = 78;
const ALTURA_DA_CELULA = 70;

// Fundo das telas de entrada: ícones de cozinha, legumes e frutas espalhados em grade, com pequenas
// variações de posição, tamanho e giro. A semente é fixa, então o desenho é o mesmo toda vez.
@Component({
  selector: 'app-fundo-de-icones',
  templateUrl: './fundo-de-icones.html',
  styleUrl: './fundo-de-icones.css',
})
export class FundoDeIcones implements AfterViewInit, OnDestroy {
  private elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  private observador?: ResizeObserver;

  private tamanho = signal({ largura: window.innerWidth, altura: window.innerHeight });

  icones = computed(() => {
    const { largura, altura } = this.tamanho();
    const colunas = Math.max(6, Math.round(largura / LARGURA_DA_CELULA));
    const linhas = Math.max(6, Math.round(altura / ALTURA_DA_CELULA));

    let semente = 7;
    const sorteio = () => {
      semente = (semente * 1664525 + 1013904223) % 4294967296;
      return semente / 4294967296;
    };

    const lista: { nome: string; x: number; y: number; tamanho: number; giro: number }[] = [];
    let fila: string[] = [];
    let anterior = '';

    for (let linha = 0; linha < linhas; linha++) {
      for (let coluna = 0; coluna < colunas; coluna++) {
        if (fila.length === 0) fila = [...NOMES].sort(() => sorteio() - 0.5);
        let nome = fila.pop() as string;
        // Sem repetir o mesmo ícone em sequência
        if (nome === anterior && fila.length) nome = fila.shift() as string;
        anterior = nome;

        // Linhas alternadas ficam deslocadas meia célula, como num azulejo
        const deslocamento = linha % 2 ? 0.5 : 0;
        lista.push({
          nome,
          x: ((coluna + 0.5 + deslocamento + (sorteio() - 0.5) * 0.35) / (colunas + 0.5)) * 100,
          y: ((linha + 0.5 + (sorteio() - 0.5) * 0.35) / linhas) * 100,
          tamanho: Math.round(30 + sorteio() * 12),
          giro: Math.round((sorteio() - 0.5) * 44),
        });
      }
    }
    return lista;
  });

  ngAfterViewInit(): void {
    if (typeof ResizeObserver === 'undefined') return;
    const el = this.elemento.nativeElement;
    this.observador = new ResizeObserver(() => {
      if (el.clientWidth > 0 && el.clientHeight > 0) this.tamanho.set({ largura: el.clientWidth, altura: el.clientHeight });
    });
    this.observador.observe(el);
  }

  ngOnDestroy(): void {
    this.observador?.disconnect();
  }
}
