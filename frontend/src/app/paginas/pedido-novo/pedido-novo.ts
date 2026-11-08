import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { CardapioService, Categoria, NOMES_DOS_DESTINOS, Produto } from '../../servicos/cardapio.service';
import { Comanda, ComandasService } from '../../servicos/comandas.service';
import { mensagemDoErro } from '../../servicos/erros';

interface Linha {
  produto: Produto;
  quantidade: number;
  observacao: string;
}

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
// Sugestões rápidas por destino: prato é com a cozinha, bebida é com o bar e o que não é preparado não tem sugestão
const OBSERVACOES: Record<string, string[]> = {
  COZINHA: ['Sem cebola', 'Bem passado', 'Ao ponto', 'Pouco sal', 'Sem pimenta'],
  BAR: ['Sem gelo', 'Pouco gelo', 'Sem açúcar', 'Sem limão', 'Sem canudo'],
  SEM_PREPARO: [],
};
const MAXIMO_DA_OBSERVACAO = 80;

@Component({
  selector: 'app-pedido-novo',
  imports: [RouterLink],
  templateUrl: './pedido-novo.html',
  styleUrl: './pedido-novo.css',
})
export class PedidoNovo implements OnInit {
  private rota = inject(ActivatedRoute);
  private roteador = inject(Router);
  private cardapio = inject(CardapioService);
  private comandas = inject(ComandasService);

  nomesDosDestinos = NOMES_DOS_DESTINOS;
  observacoesDe(produto: Produto): string[] {
    return OBSERVACOES[produto.destinoPreparo] ?? [];
  }

  comanda = signal<Comanda | null>(null);
  produtos = signal<Produto[]>([]);
  categorias = signal<Categoria[]>([]);
  carregando = signal(true);
  erro = signal('');

  etapa = signal<'escolher' | 'revisar'>('escolher');
  categoriaEscolhida = signal<number | null>(null);
  busca = signal('');
  linhas = signal<Linha[]>([]);
  enviando = signal(false);

  // Só o que dá para vender agora
  vendaveis = computed(() => this.produtos().filter((p) => p.ativo && p.disponivel));

  visiveis = computed(() => {
    const categoria = this.categoriaEscolhida();
    const termo = this.busca().trim().toLowerCase();
    return this.vendaveis().filter(
      (p) => (categoria === null || p.categoriaId === categoria) && (!termo || p.nome.toLowerCase().includes(termo)),
    );
  });

  categoriasComProduto = computed(() => this.categorias().filter((c) => this.vendaveis().some((p) => p.categoriaId === c.id)));

  totalDeItens = computed(() => this.linhas().reduce((soma, l) => soma + l.quantidade, 0));
  totalEstimado = computed(() => this.linhas().reduce((soma, l) => soma + l.quantidade * l.produto.preco, 0));

  // Um envio vira um pedido por destino: mostra para onde cada parte vai
  destinos = computed(() => {
    const nomes = new Set(this.linhas().map((l) => this.nomesDosDestinos[l.produto.destinoPreparo]));
    return Array.from(nomes);
  });

  ngOnInit(): void {
    const id = Number(this.rota.snapshot.paramMap.get('id'));
    forkJoin({
      comanda: this.comandas.detalhe(id),
      produtos: this.cardapio.listarProdutos(),
      categorias: this.cardapio.listarCategorias(),
    }).subscribe({
      next: ({ comanda, produtos, categorias }) => {
        this.comanda.set(comanda);
        this.produtos.set(produtos);
        this.categorias.set(categorias);
        this.carregando.set(false);
        if (comanda.status !== 'ABERTA') this.erro.set('Esta comanda não está aberta.');
      },
      error: (erro: HttpErrorResponse) => {
        this.erro.set(mensagemDoErro(erro));
        this.carregando.set(false);
      },
    });
  }

  preco(valor: number): string {
    return MOEDA.format(valor);
  }

  urlDaFoto(produto: Produto): string | null {
    return this.cardapio.urlDaImagem(produto);
  }

  quantidadeDe(produto: Produto): number {
    return this.linhas().find((l) => l.produto.id === produto.id)?.quantidade ?? 0;
  }

  // Quanto dá para pedir: 50 por item, ou o que sobrou no estoque quando o produto é controlado
  limiteDe(produto: Produto): number {
    return produto.controlaEstoque ? Math.min(50, produto.estoqueAtual) : 50;
  }

  avisoDeEstoque(produto: Produto): string {
    if (!produto.controlaEstoque) return '';
    if (produto.estoqueAtual === 0) return 'Esgotado';
    return produto.estoqueAtual <= produto.estoqueMinimo ? `Restam ${produto.estoqueAtual}` : '';
  }

  mudarQuantidade(produto: Produto, diferenca: number): void {
    this.linhas.update((linhas) => {
      const existente = linhas.find((l) => l.produto.id === produto.id);
      if (!existente) {
        return diferenca > 0 && this.limiteDe(produto) >= 1 ? [...linhas, { produto, quantidade: 1, observacao: '' }] : linhas;
      }
      const quantidade = Math.min(this.limiteDe(produto), existente.quantidade + diferenca);
      if (quantidade <= 0) return linhas.filter((l) => l.produto.id !== produto.id);
      return linhas.map((l) => (l.produto.id === produto.id ? { ...l, quantidade } : l));
    });
  }

  remover(linha: Linha): void {
    this.linhas.update((linhas) => linhas.filter((l) => l.produto.id !== linha.produto.id));
    if (this.linhas().length === 0) this.etapa.set('escolher');
  }

  alterarObservacao(linha: Linha, texto: string): void {
    this.linhas.update((linhas) =>
      linhas.map((l) => (l.produto.id === linha.produto.id ? { ...l, observacao: texto.slice(0, MAXIMO_DA_OBSERVACAO) } : l)),
    );
  }

  // Liga ou desliga uma observação rápida (os botões "Sem cebola", "Ao ponto"...)
  alternarObservacao(linha: Linha, rapida: string): void {
    const atual = this.linhas().find((l) => l.produto.id === linha.produto.id)?.observacao ?? '';
    const partes = atual ? atual.split(', ') : [];
    const novas = partes.includes(rapida) ? partes.filter((p) => p !== rapida) : [...partes, rapida];
    this.alterarObservacao(linha, novas.join(', '));
  }

  temObservacao(linha: Linha, rapida: string): boolean {
    return linha.observacao.split(', ').includes(rapida);
  }

  revisar(): void {
    if (this.linhas().length > 0) this.etapa.set('revisar');
  }

  voltar(): void {
    this.etapa.set('escolher');
  }

  enviar(): void {
    const comanda = this.comanda();
    if (!comanda || this.linhas().length === 0) return;

    this.enviando.set(true);
    this.erro.set('');
    const itens = this.linhas().map((l) => ({ produtoId: l.produto.id, quantidade: l.quantidade, observacao: l.observacao }));
    this.comandas.lancarPedido(comanda.id, itens).subscribe({
      next: () => this.roteador.navigateByUrl('/mesas'),
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.erro.set(mensagemDoErro(erro));
      },
    });
  }
}
