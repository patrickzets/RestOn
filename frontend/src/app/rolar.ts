// Em tela estreita a gaveta de detalhes aparece abaixo da lista: leva a tela até ela
export function rolarAteAGaveta(): void {
  if (window.innerWidth > 1000) return;
  setTimeout(() => document.querySelector('.gaveta')?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
}
