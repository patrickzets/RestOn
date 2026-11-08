import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { GavetaAcessivel } from '../../layout/gaveta-acessivel';
import { AuthService, NOMES_DOS_PERFIS, Perfil } from '../../servicos/auth.service';
import { mensagemDoErro } from '../../servicos/erros';
import { Funcionario, FuncionariosService } from '../../servicos/funcionarios.service';

const TODOS_OS_PERFIS: Perfil[] = ['ADMIN', 'GERENTE', 'GARCOM', 'CAIXA', 'COZINHA', 'BARTENDER'];

type AcaoDoPainel = 'bloquear' | 'desbloquear' | 'desativar' | 'ativar' | 'redefinir';

@Component({
  selector: 'app-equipe',
  imports: [DatePipe, ReactiveFormsModule, GavetaAcessivel],
  templateUrl: './equipe.html',
  styleUrl: './equipe.css',
})
export class Equipe implements OnInit {
  private api = inject(FuncionariosService);
  private auth = inject(AuthService);
  private fb = inject(NonNullableFormBuilder);

  nomesDosPerfis = NOMES_DOS_PERFIS;

  funcionarios = signal<Funcionario[]>([]);
  carregando = signal(true);
  erroDaLista = signal('');

  painel = signal<'fechado' | 'novo' | 'editar'>('fechado');
  selecionado = signal<Funcionario | null>(null);
  salvando = signal(false);
  erroDoPainel = signal('');
  confirmando = signal<AcaoDoPainel | null>(null);

  // Senha provisória mostrada uma única vez, logo depois de criar ou redefinir
  avisoDeSenha = signal<{ titulo: string; senha: string } | null>(null);
  copiado = signal(false);

  form = this.fb.group({
    nome: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    login: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(40), Validators.pattern(/^[a-zA-Z0-9._-]+$/)]],
    perfil: ['GARCOM' as Perfil, Validators.required],
  });

  // O Gerente não escolhe o perfil Administrador
  perfisDisponiveis = computed(() => (this.auth.usuario()?.perfil === 'ADMIN' ? TODOS_OS_PERFIS : TODOS_OS_PERFIS.filter((p) => p !== 'ADMIN')));

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erroDaLista.set('');
    this.api.listar().subscribe({
      next: (lista) => {
        this.funcionarios.set(lista);
        this.carregando.set(false);
      },
      error: (erro: HttpErrorResponse) => {
        this.erroDaLista.set(mensagemDoErro(erro));
        this.carregando.set(false);
      },
    });
  }


  novo(): void {
    this.avisoDeSenha.set(null);
    this.selecionado.set(null);
    this.limparEstadoDoPainel();
    this.form.reset({ nome: '', login: '', perfil: 'GARCOM' });
    this.form.controls.login.enable();
    this.painel.set('novo');
  }

  editar(funcionario: Funcionario): void {
    this.selecionado.set(funcionario);
    this.limparEstadoDoPainel();
    this.form.reset({ nome: funcionario.nome, login: funcionario.login, perfil: funcionario.perfil });
    this.form.controls.login.disable(); // o login não muda depois de criado
    this.painel.set('editar');
  }

  fechar(): void {
    this.painel.set('fechado');
    this.selecionado.set(null);
    this.limparEstadoDoPainel();
  }

  salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.salvando.set(true);
    this.erroDoPainel.set('');
    const { nome, login, perfil } = this.form.getRawValue();
    const atual = this.selecionado();

    if (this.painel() === 'novo') {
      this.api.criar({ nome, login, perfil }).subscribe({
        next: (resposta) => {
          this.salvando.set(false);
          this.avisoDeSenha.set({ titulo: `Funcionário ${resposta.funcionario.nome} criado`, senha: resposta.senhaProvisoria });
          this.fechar();
          this.carregar();
        },
        error: (erro: HttpErrorResponse) => this.falhar(erro),
      });
    } else if (atual) {
      this.api.atualizar(atual.id, { nome, perfil }).subscribe({
        next: (atualizado) => {
          this.salvando.set(false);
          this.substituir(atualizado);
          this.selecionado.set(atualizado);
        },
        error: (erro: HttpErrorResponse) => this.falhar(erro),
      });
    }
  }


  pedirConfirmacao(acao: AcaoDoPainel): void {
    this.erroDoPainel.set('');
    this.confirmando.set(acao);
  }

  cancelarConfirmacao(): void {
    this.confirmando.set(null);
  }

  confirmar(): void {
    const acao = this.confirmando();
    const alvo = this.selecionado();
    if (!acao || !alvo) return;

    this.salvando.set(true);
    this.erroDoPainel.set('');

    if (acao === 'redefinir') {
      this.api.redefinirSenha(alvo.id).subscribe({
        next: (resposta) => {
          this.avisoDeSenha.set({ titulo: `Nova senha provisória de ${alvo.nome}`, senha: resposta.senhaProvisoria });
          this.carregar();
          this.fechar();
        },
        error: (erro: HttpErrorResponse) => this.falhar(erro),
      });
      return;
    }

    this.api.mudarSituacao(alvo.id, acao).subscribe({
      next: (atualizado) => {
        this.salvando.set(false);
        this.confirmando.set(null);
        this.substituir(atualizado);
        this.selecionado.set(atualizado);
      },
      error: (erro: HttpErrorResponse) => this.falhar(erro),
    });
  }

  copiarSenha(): void {
    const aviso = this.avisoDeSenha();
    if (!aviso) return;
    navigator.clipboard?.writeText(aviso.senha).then(() => this.copiado.set(true));
  }

  fecharAviso(): void {
    this.avisoDeSenha.set(null);
    this.copiado.set(false);
  }


  ehEu(funcionario: Funcionario): boolean {
    return funcionario.id === this.auth.usuario()?.id;
  }

  podeGerenciar(funcionario: Funcionario): boolean {
    const eu = this.auth.usuario();
    return !!eu && !(eu.perfil === 'GERENTE' && funcionario.perfil === 'ADMIN');
  }

  iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    return ((partes[0]?.[0] ?? '') + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase();
  }

  invalido(campo: 'nome' | 'login'): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && controle.touched;
  }


  private substituir(atualizado: Funcionario): void {
    this.funcionarios.update((lista) => lista.map((f) => (f.id === atualizado.id ? atualizado : f)));
  }

  private limparEstadoDoPainel(): void {
    this.erroDoPainel.set('');
    this.confirmando.set(null);
    this.salvando.set(false);
  }

  private falhar(erro: HttpErrorResponse): void {
    this.salvando.set(false);
    this.confirmando.set(null);
    this.erroDoPainel.set(mensagemDoErro(erro));
  }
}
