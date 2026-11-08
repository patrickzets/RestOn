import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { FundoDeIcones } from '../../layout/fundo-de-icones';
import { AuthService } from '../../servicos/auth.service';

@Component({
  selector: 'app-trocar-senha',
  imports: [ReactiveFormsModule, FundoDeIcones],
  templateUrl: './trocar-senha.html',
  styleUrl: './trocar-senha.css',
})
export class TrocarSenha {
  private auth = inject(AuthService);
  private router = inject(Router);

  usuario = this.auth.usuario;
  provisoria = computed(() => this.usuario()?.senhaProvisoria === true);

  form = inject(NonNullableFormBuilder).group({
    senhaAtual: ['', Validators.required],
    novaSenha: ['', Validators.required],
    confirmacao: ['', Validators.required],
  });

  private novaSenha = toSignal(this.form.controls.novaSenha.valueChanges, { initialValue: '' });
  private confirmacao = toSignal(this.form.controls.confirmacao.valueChanges, { initialValue: '' });
  private senhaAtual = toSignal(this.form.controls.senhaAtual.valueChanges, { initialValue: '' });

  // As mesmas regras do servidor, mostradas enquanto a pessoa digita
  regras = computed(() => {
    const senha = this.novaSenha();
    return [
      { texto: 'Pelo menos 10 caracteres', ok: senha.length >= 10 },
      { texto: 'Letras e números', ok: /[A-Za-zÀ-ÿ]/.test(senha) && /\d/.test(senha) },
      { texto: 'A confirmação é igual', ok: senha.length > 0 && senha === this.confirmacao() },
    ];
  });

  podeSalvar = computed(() => this.regras().every((regra) => regra.ok) && this.senhaAtual().length > 0);

  mostrar = signal(false);
  tipoDoCampo = computed(() => (this.mostrar() ? 'text' : 'password'));

  enviando = signal(false);
  erro = signal('');

  salvar(): void {
    if (!this.podeSalvar()) return;

    this.enviando.set(true);
    this.erro.set('');
    const { senhaAtual, novaSenha } = this.form.getRawValue();

    this.auth.alterarSenha(senhaAtual, novaSenha).subscribe({
      next: () => this.router.navigateByUrl(this.paginaInicial()),
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.erro.set(erro.error?.detail ?? 'Não consegui trocar a senha. Tente de novo.');
      },
    });
  }

  voltar(): void {
    this.router.navigateByUrl(this.paginaInicial());
  }

  sair(): void {
    this.auth.sair().subscribe(() => this.router.navigateByUrl('/login'));
  }

  private paginaInicial(): string {
    const usuario = this.usuario();
    return usuario ? this.auth.paginaInicial(usuario.perfil) : '/login';
  }
}
