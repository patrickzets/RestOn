import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { mensagemDoErro } from '../../servicos/erros';
import { Painel, RelatoriosService } from '../../servicos/relatorios.service';

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

@Component({
  selector: 'app-visao-geral',
  imports: [RouterLink],
  templateUrl: './visao-geral.html',
  styleUrl: './visao-geral.css',
})
export class VisaoGeral implements OnInit {
  private relatorios = inject(RelatoriosService);
  private destroyRef = inject(DestroyRef);

  painel = signal<Painel | null>(null);
  carregando = signal(true);
  erro = signal('');


  semVendas = computed(() => {
    const p = this.painel();
    return !!p && p.comandasFechadasHoje === 0 && p.vendasPorHora.length === 0;
  });

  // do menor ao maior horário com venda, altura relativa à maior barra
  barras = computed(() => {
    const horas = [...(this.painel()?.vendasPorHora ?? [])].sort((a, b) => a.hora - b.hora);
    const maior = Math.max(0, ...horas.map((h) => h.total));
    return horas.map((h) => ({
      rotulo: String(h.hora).padStart(2, '0') + 'h',
      valor: this.moeda(h.total),
      curto: Math.round(h.total).toLocaleString('pt-BR'),
      altura: maior > 0 ? Math.max(4, Math.round((h.total / maior) * 100)) : 4,
    }));
  });

  ngOnInit(): void {
    this.carregar();
    // recarrega sozinho, sem voltar para o "carregando"
    const relogio = setInterval(() => this.carregar(), 30000);
    this.destroyRef.onDestroy(() => clearInterval(relogio));
  }

  carregar(): void {
    this.relatorios.painel().subscribe({
      next: (painel) => {
        this.painel.set(painel);
        this.erro.set('');
        this.carregando.set(false);
      },
      error: (erro: HttpErrorResponse) => {
        this.erro.set(mensagemDoErro(erro));
        this.carregando.set(false);
      },
    });
  }

  moeda(valor: number): string {
    return MOEDA.format(valor);
  }
}
