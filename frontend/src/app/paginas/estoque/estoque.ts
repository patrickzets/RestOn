import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { GavetaAcessivel } from '../../layout/gaveta-acessivel';
import { mensagemDoErro } from '../../servicos/erros';
import { EstoqueService, ItemDeEstoque, Movimentacao, NOMES_DAS_MOVIMENTACOES } from '../../servicos/estoque.service';
import { rolarAteAGaveta } from '../../rolar';

type Filtro = 'controlados' | 'alerta' | 'sem-controle';

@Component({
  selector: 'app-estoque',
  imports: [DatePipe, ReactiveFormsModule, GavetaAcessivel],
  templateUrl: './estoque.html',
  styleUrl: './estoque.css',
})
export class Estoque implements OnInit {
  private api = inject(EstoqueService);
  private fb = inject(NonNullableFormBuilder);

  nomesDasMovimentacoes = NOMES_DAS_MOVIMENTACOES;

  itens = signal<ItemDeEstoque[]>([]);
  carregando = signal(true);
  erroDaLista = signal('');
  filtro = signal<Filtro>('controlados');
  busca = signal('');

  selecionadoId = signal<number | null>(null);
  historico = signal<Movimentacao[]>([]);
  salvando = signal(false);
  erroDoPainel = signal('');
  aviso = signal('');
  confirmandoDesligar = signal(false);

  formEntrada = this.fb.group({
    quantidade: [1, [Validators.required, Validators.min(1), Validators.max(100000)]],
    observacao: ['', Validators.maxLength(120)],
  });
  formAjuste = this.fb.group({
    quantidade: [0, [Validators.required, Validators.min(0), Validators.max(100000)]],
    motivo: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(120)]],
  });
  formControle = this.fb.group({
    minimo: [0, [Validators.required, Validators.min(0), Validators.max(100000)]],
    quantidadeInicial: [0, [Validators.required, Validators.min(0), Validators.max(100000)]],
  });

  selecionado = computed(() => this.itens().find((i) => i.produtoId === this.selecionadoId()) ?? null);

  contagem = computed(() => {
    const lista = this.itens();
    return {
      controlados: lista.filter((i) => i.controla).length,
      alerta: lista.filter((i) => i.situacao === 'BAIXO' || i.situacao === 'ZERADO').length,
      semControle: lista.filter((i) => !i.controla).length,
    };
  });

  visiveis = computed(() => {
    const texto = this.busca().trim().toLowerCase();
    return this.itens()
      .filter((i) => {
        switch (this.filtro()) {
          case 'controlados':
            return i.controla;
          case 'alerta':
            return i.situacao === 'BAIXO' || i.situacao === 'ZERADO';
          default:
            return !i.controla;
        }
      })
      .filter((i) => !texto || i.nome.toLowerCase().includes(texto) || i.categoriaNome.toLowerCase().includes(texto))
      .sort((a, b) => this.peso(a) - this.peso(b) || a.nome.localeCompare(b.nome, 'pt-BR'));
  });

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erroDaLista.set('');
    this.api.listar().subscribe({
      next: (lista) => {
        this.itens.set(lista);
        this.carregando.set(false);
        // Quem ainda não controla nada começa vendo a lista dos produtos sem controle
        if (this.contagem().controlados === 0 && this.contagem().semControle > 0) {
          this.filtro.set('sem-controle');
        }
      },
      error: (erro: HttpErrorResponse) => {
        this.erroDaLista.set(mensagemDoErro(erro));
        this.carregando.set(false);
      },
    });
  }

  abrir(item: ItemDeEstoque): void {
    this.selecionadoId.set(item.produtoId);
    this.limparPainel();
    this.formEntrada.reset({ quantidade: 1, observacao: '' });
    this.formAjuste.reset({ quantidade: item.quantidade, motivo: '' });
    this.formControle.reset({ minimo: item.minimo, quantidadeInicial: 0 });
    if (item.controla) this.carregarHistorico(item.produtoId);
    rolarAteAGaveta();
  }

  fechar(): void {
    this.selecionadoId.set(null);
    this.limparPainel();
  }

  registrarEntrada(): void {
    const item = this.selecionado();
    if (!item) return;
    if (this.formEntrada.invalid) {
      this.formEntrada.markAllAsTouched();
      return;
    }
    const { quantidade, observacao } = this.formEntrada.getRawValue();
    this.executar(this.api.entrada(item.produtoId, quantidade, observacao), `Entrada de ${quantidade} registrada.`);
    this.formEntrada.reset({ quantidade: 1, observacao: '' });
  }

  registrarAjuste(): void {
    const item = this.selecionado();
    if (!item) return;
    if (this.formAjuste.invalid) {
      this.formAjuste.markAllAsTouched();
      return;
    }
    const { quantidade, motivo } = this.formAjuste.getRawValue();
    this.executar(this.api.ajustar(item.produtoId, quantidade, motivo), 'Saldo ajustado.');
  }

  salvarControle(ligar: boolean): void {
    const item = this.selecionado();
    if (!item) return;
    if (this.formControle.invalid) {
      this.formControle.markAllAsTouched();
      return;
    }
    const { minimo, quantidadeInicial } = this.formControle.getRawValue();
    const texto = ligar ? (item.controla ? 'Estoque mínimo salvo.' : 'Controle de estoque ligado.') : 'Controle de estoque desligado.';
    this.confirmandoDesligar.set(false);
    this.executar(this.api.configurar(item.produtoId, ligar, minimo, quantidadeInicial), texto);
  }

  pedirParaDesligar(): void {
    this.confirmandoDesligar.set(true);
  }

  invalido(form: 'entrada' | 'ajuste' | 'controle', campo: string): boolean {
    const grupo = form === 'entrada' ? this.formEntrada : form === 'ajuste' ? this.formAjuste : this.formControle;
    const controle = (grupo as FormGroup).get(campo);
    return !!controle && controle.invalid && controle.touched;
  }

  private executar(chamada: ReturnType<EstoqueService['entrada']>, mensagem: string): void {
    this.salvando.set(true);
    this.erroDoPainel.set('');
    this.aviso.set('');
    chamada.subscribe({
      next: (atualizado) => {
        this.salvando.set(false);
        this.itens.update((lista) => lista.map((i) => (i.produtoId === atualizado.produtoId ? atualizado : i)));
        this.aviso.set(mensagem);
        this.formAjuste.reset({ quantidade: atualizado.quantidade, motivo: '' });
        this.formControle.reset({ minimo: atualizado.minimo, quantidadeInicial: 0 });
        if (atualizado.controla) this.carregarHistorico(atualizado.produtoId);
        else this.historico.set([]);
      },
      error: (erro: HttpErrorResponse) => {
        this.salvando.set(false);
        this.erroDoPainel.set(mensagemDoErro(erro));
      },
    });
  }

  private carregarHistorico(produtoId: number): void {
    this.api.historico(produtoId).subscribe({
      next: (lista) => this.historico.set(lista),
      error: () => this.historico.set([]),
    });
  }

  private limparPainel(): void {
    this.historico.set([]);
    this.erroDoPainel.set('');
    this.aviso.set('');
    this.confirmandoDesligar.set(false);
  }

  // Zerado primeiro, depois baixo, depois o resto
  private peso(item: ItemDeEstoque): number {
    return item.situacao === 'ZERADO' ? 0 : item.situacao === 'BAIXO' ? 1 : 2;
  }
}
