import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';

import { CaixaService, NOMES_DOS_METODOS, Turno } from '../../servicos/caixa.service';
import { mensagemDoErro } from '../../servicos/erros';
import { RelatoriosService, Vendas } from '../../servicos/relatorios.service';

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

type Aba = 'vendas' | 'turnos';

// yyyy-MM-dd no fuso local (toISOString usaria UTC e erraria o dia à noite)
function formatarData(data: Date): string {
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}

function diasAtras(dias: number): string {
  const data = new Date();
  data.setDate(data.getDate() - dias);
  return formatarData(data);
}

@Component({
  selector: 'app-relatorios',
  imports: [DatePipe],
  templateUrl: './relatorios.html',
  styleUrl: './relatorios.css',
})
export class Relatorios implements OnInit {
  private relatorios = inject(RelatoriosService);
  private caixa = inject(CaixaService);

  nomesDosMetodos = NOMES_DOS_METODOS;

  aba = signal<Aba>('vendas');
  de = signal('');
  ate = signal('');
  vendas = signal<Vendas | null>(null);
  turnos = signal<Turno[]>([]);
  carregando = signal(true);
  carregandoTurnos = signal(true);
  erro = signal('');
  erroDosTurnos = signal('');

  maiorDia = computed(() => Math.max(0, ...(this.vendas()?.porDia ?? []).map((d) => d.total)));
  // Com muitos dias não cabe um rótulo por barra
  passoDosRotulos = computed(() => {
    const total = this.vendas()?.porDia.length ?? 0;
    return total > 31 ? Math.ceil(total / 8) : 1;
  });

  ngOnInit(): void {
    this.buscar();
    this.caixa.historicoDeTurnos().subscribe({
      next: (lista) => {
        this.turnos.set(lista);
        this.carregandoTurnos.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.erroDosTurnos.set(mensagemDoErro(e));
        this.carregandoTurnos.set(false);
      },
    });
  }

  atalho(dias: number): void {
    this.de.set(diasAtras(dias - 1));
    this.ate.set(diasAtras(0));
    this.aplicar();
  }

  aplicar(): void {
    const de = this.de();
    const ate = this.ate();
    if (!de && !ate) {
      this.buscar();
      return;
    }
    if (!de || !ate) {
      this.erro.set('Informe as duas datas.');
      return;
    }
    if (de > ate) {
      this.erro.set('A data inicial não pode ser depois da final.');
      return;
    }
    const dias = (Date.parse(ate) - Date.parse(de)) / 86_400_000;
    if (dias > 366) {
      this.erro.set('O período pode ter no máximo 366 dias.');
      return;
    }
    this.buscar(de, ate);
  }

  private buscar(de?: string, ate?: string): void {
    this.carregando.set(true);
    this.erro.set('');
    this.relatorios.vendas(de, ate).subscribe({
      next: (v) => {
        this.vendas.set(v);
        this.de.set(v.de);
        this.ate.set(v.ate);
        this.carregando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.erro.set(mensagemDoErro(e));
        this.carregando.set(false);
      },
    });
  }

  altura(total: number): number {
    const maior = this.maiorDia();
    return maior > 0 ? Math.max(2, (total / maior) * 100) : 0;
  }

  mostrarRotulo(indice: number): boolean {
    return indice % this.passoDosRotulos() === 0;
  }

  // yyyy-MM-dd para dd/MM sem passar por Date (evita deslocar o dia pelo fuso)
  diaMes(data: string): string {
    return `${data.slice(8, 10)}/${data.slice(5, 7)}`;
  }

  moeda(valor: number): string {
    return MOEDA.format(valor);
  }

  imprimir(): void {
    window.print();
  }
}
