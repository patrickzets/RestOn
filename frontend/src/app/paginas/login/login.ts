import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { FundoDeIcones } from '../../layout/fundo-de-icones';
import { AuthService } from '../../servicos/auth.service';

const CHAVE_DO_LOGIN = 'reston.login';

// O armazenamento do navegador pode estar bloqueado (janela privada), então tudo vai em try/catch
function lerLoginLembrado(): string {
  try {
    return localStorage.getItem(CHAVE_DO_LOGIN) ?? '';
  } catch {
    return '';
  }
}

function guardarLogin(login: string | null): void {
  try {
    if (login) localStorage.setItem(CHAVE_DO_LOGIN, login);
    else localStorage.removeItem(CHAVE_DO_LOGIN);
  } catch {
    // sem armazenamento: o login só não fica lembrado
  }
}

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, FundoDeIcones],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login implements OnInit {
  private auth = inject(AuthService);
  private router = inject(Router);

  form = inject(NonNullableFormBuilder).group({
    login: ['', Validators.required],
    senha: ['', Validators.required],
    lembrar: [false],
  });

  mostrarSenha = signal(false);
  capsLigado = signal(false);
  enviando = signal(false);
  erro = signal('');

  ngOnInit(): void {
    const lembrado = lerLoginLembrado();
    if (lembrado) this.form.patchValue({ login: lembrado, lembrar: true });
  }

  // Caps Lock ligado atrapalha a digitação da senha, então avisamos
  verCapsLock(evento: KeyboardEvent): void {
    this.capsLigado.set(evento.getModifierState?.('CapsLock') ?? false);
  }

  invalido(campo: 'login' | 'senha'): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && controle.touched;
  }

  entrar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.erro.set('');
    const { login, senha, lembrar } = this.form.getRawValue();

    this.auth.entrar(login.trim(), senha).subscribe({
      next: (usuario) => {
        guardarLogin(lembrar ? login.trim() : null);
        this.router.navigateByUrl(usuario.senhaProvisoria ? '/trocar-senha' : this.auth.paginaInicial(usuario.perfil));
      },
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        if (erro.status === 401) {
          this.erro.set('Login ou senha inválidos.');
        } else if (erro.status === 0) {
          this.erro.set('Não consegui falar com o servidor. Confira se a API está no ar.');
        } else {
          this.erro.set(erro.error?.detail ?? 'Algo deu errado. Tente de novo.');
        }
      },
    });
  }
}
