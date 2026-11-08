import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe, Location } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { CaixaService, Comprovante as DadosDoComprovante, NOMES_DOS_METODOS } from '../../servicos/caixa.service';
import { mensagemDoErro } from '../../servicos/erros';

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

@Component({
  selector: 'app-comprovante',
  imports: [DatePipe],
  templateUrl: './comprovante.html',
  styleUrl: './comprovante.css',
})
export class Comprovante implements OnInit {
  private api = inject(CaixaService);
  private rota = inject(ActivatedRoute);
  private localizacao = inject(Location);

  nomesDosMetodos = NOMES_DOS_METODOS;

  dados = signal<DadosDoComprovante | null>(null);
  erro = signal('');

  ngOnInit(): void {
    const id = Number(this.rota.snapshot.paramMap.get('id'));
    this.api.comprovante(id).subscribe({
      next: (resposta) => this.dados.set(resposta),
      error: (erro: HttpErrorResponse) => this.erro.set(mensagemDoErro(erro)),
    });
  }

  reais(valor: number): string {
    return MOEDA.format(valor);
  }

  imprimir(): void {
    window.print();
  }

  voltar(): void {
    this.localizacao.back();
  }
}
