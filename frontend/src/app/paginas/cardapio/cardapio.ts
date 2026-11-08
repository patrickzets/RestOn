import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, forkJoin } from 'rxjs';

import {
  CardapioService,
  Categoria,
  DestinoPreparo,
  NOMES_DOS_DESTINOS,
  Produto,
} from '../../servicos/cardapio.service';
import { GavetaAcessivel } from '../../layout/gaveta-acessivel';
import { mensagemDoErro } from '../../servicos/erros';

const TAMANHO_MAXIMO_DA_FOTO = 5 * 1024 * 1024;
const TIPOS_DE_FOTO = ['image/jpeg', 'image/png', 'image/webp'];
const DESTINOS: DestinoPreparo[] = ['COZINHA', 'BAR', 'SEM_PREPARO'];
const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

@Component({
  selector: 'app-cardapio',
  imports: [ReactiveFormsModule, GavetaAcessivel],
  templateUrl: './cardapio.html',
  styleUrl: './cardapio.css',
})
export class Cardapio implements OnInit, OnDestroy {
  private api = inject(CardapioService);
  private fb = inject(NonNullableFormBuilder);

  nomesDosDestinos = NOMES_DOS_DESTINOS;
  destinos = DESTINOS;

  produtos = signal<Produto[]>([]);
  categorias = signal<Categoria[]>([]);
  carregando = signal(true);
  erroDaLista = signal('');

  categoriaEscolhida = signal<number | null>(null); // null = todas
  busca = signal('');
  modo = signal<'cards' | 'lista'>('cards');
  mostrarArquivados = signal(false);

  painel = signal<'fechado' | 'novo' | 'editar' | 'categorias'>('fechado');
  selecionadoId = signal<number | null>(null);
  salvando = signal(false);
  erroDoPainel = signal('');
  confirmandoArquivar = signal(false);

  // Foto escolhida para um produto novo: só é enviada depois que o produto existe
  fotoPendente = signal<File | null>(null);
  previaLocal = signal<string | null>(null);

  // Painel de categorias
  novaCategoria = signal('');
  categoriaEditando = signal<number | null>(null);
  categoriaExcluindo = signal<number | null>(null); // pede confirmação antes de excluir
  nomeEditado = signal('');

  form = this.fb.group({
    nome: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    descricao: ['', Validators.maxLength(160)],
    categoriaId: [0, [Validators.required, Validators.min(1)]],
    preco: [0, [Validators.required, Validators.min(0.01), Validators.max(99999.99)]],
    tempoPreparoMin: [0, [Validators.required, Validators.min(0), Validators.max(240)]],
    destinoPreparo: ['COZINHA' as DestinoPreparo, Validators.required],
  });

  selecionado = computed(() => this.produtos().find((p) => p.id === this.selecionadoId()) ?? null);

  // Produtos que entram na lista, depois da categoria, da busca e do filtro de arquivados
  listaBase = computed(() => this.produtos().filter((p) => p.ativo || this.mostrarArquivados()));

  visiveis = computed(() => {
    const categoria = this.categoriaEscolhida();
    const termo = this.busca().trim().toLowerCase();
    return this.listaBase().filter(
      (p) =>
        (categoria === null || p.categoriaId === categoria) &&
        (!termo || p.nome.toLowerCase().includes(termo) || (p.descricao ?? '').toLowerCase().includes(termo)),
    );
  });

  contagemPorCategoria = computed(() => {
    const contagem = new Map<number, number>();
    for (const p of this.listaBase()) contagem.set(p.categoriaId, (contagem.get(p.categoriaId) ?? 0) + 1);
    return contagem;
  });

  fotoDaGaveta = computed(() => {
    const atual = this.selecionado();
    return this.previaLocal() ?? (atual ? this.api.urlDaImagem(atual) : null);
  });

  ngOnInit(): void {
    this.carregar();
  }

  ngOnDestroy(): void {
    this.soltarPrevia();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erroDaLista.set('');
    forkJoin({ produtos: this.api.listarProdutos(), categorias: this.api.listarCategorias() }).subscribe({
      next: ({ produtos, categorias }) => {
        this.produtos.set(produtos);
        this.categorias.set(categorias);
        this.carregando.set(false);
      },
      error: (erro: HttpErrorResponse) => {
        this.erroDaLista.set(mensagemDoErro(erro));
        this.carregando.set(false);
      },
    });
  }

  urlDaFoto(produto: Produto): string | null {
    return this.api.urlDaImagem(produto);
  }

  preco(valor: number): string {
    return MOEDA.format(valor);
  }

  novo(): void {
    this.selecionadoId.set(null);
    this.prepararPainel();
    this.form.reset({
      nome: '',
      descricao: '',
      categoriaId: this.categoriaEscolhida() ?? this.categorias()[0]?.id ?? 0,
      preco: 0,
      tempoPreparoMin: 0,
      destinoPreparo: 'COZINHA',
    });
    this.painel.set('novo');
  }

  editar(produto: Produto): void {
    this.selecionadoId.set(produto.id);
    this.prepararPainel();
    this.form.reset({
      nome: produto.nome,
      descricao: produto.descricao ?? '',
      categoriaId: produto.categoriaId,
      preco: produto.preco,
      tempoPreparoMin: produto.tempoPreparoMin,
      destinoPreparo: produto.destinoPreparo,
    });
    this.painel.set('editar');
  }

  fechar(): void {
    this.painel.set('fechado');
    this.selecionadoId.set(null);
    this.prepararPainel();
  }

  salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const dados = this.form.getRawValue();
    this.salvando.set(true);
    this.erroDoPainel.set('');

    if (this.painel() === 'novo') {
      this.api.criarProduto(dados).subscribe({
        next: (criado) => this.depoisDeCriar(criado),
        error: (erro: HttpErrorResponse) => this.falhar(erro),
      });
      return;
    }

    const atual = this.selecionado();
    if (!atual) return;
    this.api.atualizarProduto(atual.id, dados).subscribe({
      next: (atualizado) => {
        this.salvando.set(false);
        this.substituir(atualizado);
      },
      error: (erro: HttpErrorResponse) => this.falhar(erro),
    });
  }

  // Liga ou desliga a venda do produto (vale no cartão e na gaveta)
  alternarDisponibilidade(produto: Produto, evento?: Event): void {
    evento?.stopPropagation();
    this.api.mudarDisponibilidade(produto.id, !produto.disponivel).subscribe({
      next: (atualizado) => this.substituir(atualizado),
      error: (erro: HttpErrorResponse) => this.erroDaLista.set(mensagemDoErro(erro)),
    });
  }

  mudarAtivo(ativo: boolean): void {
    const atual = this.selecionado();
    if (!atual) return;
    this.salvando.set(true);
    this.api.mudarAtivo(atual.id, ativo).subscribe({
      next: (atualizado) => {
        this.salvando.set(false);
        this.confirmandoArquivar.set(false);
        this.substituir(atualizado);
      },
      error: (erro: HttpErrorResponse) => this.falhar(erro),
    });
  }

  escolherFoto(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.[0];
    campo.value = ''; // permite escolher o mesmo arquivo de novo
    if (!arquivo) return;

    if (!TIPOS_DE_FOTO.includes(arquivo.type)) {
      this.erroDoPainel.set('Use uma imagem JPG, PNG ou WebP.');
      return;
    }
    if (arquivo.size > TAMANHO_MAXIMO_DA_FOTO) {
      this.erroDoPainel.set('A imagem deve ter no máximo 5 MB.');
      return;
    }
    this.erroDoPainel.set('');

    const atual = this.selecionado();
    if (this.painel() === 'novo' || !atual) {
      this.soltarPrevia();
      this.fotoPendente.set(arquivo);
      this.previaLocal.set(URL.createObjectURL(arquivo));
      return;
    }

    this.salvando.set(true);
    this.api.enviarImagem(atual.id, arquivo).subscribe({
      next: (atualizado) => {
        this.salvando.set(false);
        this.substituir(atualizado);
      },
      error: (erro: HttpErrorResponse) => this.falhar(erro),
    });
  }

  removerFoto(): void {
    const atual = this.selecionado();
    if (this.painel() === 'novo' || !atual) {
      this.soltarPrevia();
      return;
    }
    this.salvando.set(true);
    this.api.removerImagem(atual.id).subscribe({
      next: (atualizado) => {
        this.salvando.set(false);
        this.substituir(atualizado);
      },
      error: (erro: HttpErrorResponse) => this.falhar(erro),
    });
  }

  abrirCategorias(): void {
    this.selecionadoId.set(null);
    this.prepararPainel();
    this.novaCategoria.set('');
    this.categoriaEditando.set(null);
    this.painel.set('categorias');
  }

  adicionarCategoria(): void {
    const nome = this.novaCategoria().trim();
    if (nome.length < 2) {
      this.erroDoPainel.set('O nome da categoria deve ter pelo menos 2 letras.');
      return;
    }
    this.executarNasCategorias(this.api.criarCategoria(nome), () => this.novaCategoria.set(''));
  }

  comecarARenomear(categoria: Categoria): void {
    this.erroDoPainel.set('');
    this.categoriaEditando.set(categoria.id);
    this.nomeEditado.set(categoria.nome);
  }

  salvarNome(): void {
    const id = this.categoriaEditando();
    if (id === null) return;
    this.executarNasCategorias(this.api.renomearCategoria(id, this.nomeEditado().trim()), () => this.categoriaEditando.set(null));
  }

  moverCategoria(categoria: Categoria, direcao: 'subir' | 'descer'): void {
    this.executarNasCategorias(this.api.moverCategoria(categoria.id, direcao));
  }

  excluirCategoria(categoria: Categoria): void {
    this.categoriaExcluindo.set(null);
    this.executarNasCategorias(this.api.excluirCategoria(categoria.id), () => {
      if (this.categoriaEscolhida() === categoria.id) this.categoriaEscolhida.set(null);
    });
  }

  invalido(campo: 'nome' | 'descricao' | 'categoriaId' | 'preco' | 'tempoPreparoMin'): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && controle.touched;
  }

  private depoisDeCriar(criado: Produto): void {
    const foto = this.fotoPendente();
    if (!foto) {
      this.terminarCriacao();
      return;
    }
    this.api.enviarImagem(criado.id, foto).subscribe({
      next: () => this.terminarCriacao(),
      error: (erro: HttpErrorResponse) => {
        // O produto já existe: abre para o usuário tentar a foto de novo
        this.produtos.update((lista) => [...lista, criado]);
        this.editar(criado);
        this.erroDoPainel.set('Produto criado, mas a foto não foi enviada: ' + mensagemDoErro(erro));
        this.carregar();
      },
    });
  }

  private terminarCriacao(): void {
    this.fechar();
    this.carregar();
  }

  // Roda uma chamada do painel de categorias e recarrega as categorias e os produtos
  private executarNasCategorias(chamada: Observable<unknown>, depois?: () => void): void {
    this.salvando.set(true);
    this.erroDoPainel.set('');
    chamada.subscribe({
      next: () => {
        this.salvando.set(false);
        depois?.();
        this.carregar();
      },
      error: (erro: HttpErrorResponse) => this.falhar(erro),
    });
  }

  private substituir(atualizado: Produto): void {
    this.produtos.update((lista) => lista.map((p) => (p.id === atualizado.id ? atualizado : p)));
    this.api.listarCategorias().subscribe((categorias) => this.categorias.set(categorias));
  }

  private prepararPainel(): void {
    this.erroDoPainel.set('');
    this.salvando.set(false);
    this.confirmandoArquivar.set(false);
    this.soltarPrevia();
  }

  private soltarPrevia(): void {
    const previa = this.previaLocal();
    if (previa) URL.revokeObjectURL(previa);
    this.previaLocal.set(null);
    this.fotoPendente.set(null);
  }

  private falhar(erro: HttpErrorResponse): void {
    this.salvando.set(false);
    this.confirmandoArquivar.set(false);
    this.erroDoPainel.set(mensagemDoErro(erro));
  }
}
