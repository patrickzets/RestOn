import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { GRUPOS, MODULOS, Modulo, modulosDoPerfil } from '../modulos';
import { AuthService, NOMES_DOS_PERFIS } from '../servicos/auth.service';
import { SaudeService } from '../servicos/saude.service';

@Component({
  selector: 'app-layout-principal',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './layout-principal.html',
  styleUrl: './layout-principal.css',
})
export class LayoutPrincipal implements OnInit {
  private router = inject(Router);
  private auth = inject(AuthService);
  private saude = inject(SaudeService);
  private destroyRef = inject(DestroyRef);

  usuario = this.auth.usuario;

  // Menu: só os módulos que o perfil da pessoa pode abrir, agrupados
  grupos = computed(() => {
    const perfil = this.usuario()?.perfil;
    const permitidos = perfil ? modulosDoPerfil(perfil) : [];
    return GRUPOS.map((grupo) => ({ ...grupo, modulos: permitidos.filter((m) => m.grupo === grupo.nome) })).filter(
      (grupo) => grupo.modulos.length > 0,
    );
  });

  // O garçom trabalha no celular: ganha uma barra de atalhos embaixo, no lugar de abrir o menu
  ehGarcom = computed(() => this.usuario()?.perfil === 'GARCOM');
  atalhosDoGarcom = computed(() => modulosDoPerfil('GARCOM'));

  nomeDoPerfil = computed(() => {
    const perfil = this.usuario()?.perfil;
    return perfil ? NOMES_DOS_PERFIS[perfil] : '';
  });

  iniciais = computed(() => {
    const partes = (this.usuario()?.nome ?? '').trim().split(/\s+/);
    return ((partes[0]?.[0] ?? '') + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase();
  });

  menuAberto = signal(false);
  moduloAtual = signal<Modulo | undefined>(undefined);

  apiOk = computed(() => this.saude.estado()?.status === 'ok');

  textoStatus = computed(() => {
    const estado = this.saude.estado();
    if (!estado) return 'Verificando...';
    if (estado.status === 'ok') return 'API online';
    if (estado.status === 'banco-indisponivel') return 'Banco indisponível';
    return 'API offline';
  });

  ngOnInit(): void {
    this.moduloAtual.set(this.acharModulo(this.router.url));
    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.moduloAtual.set(this.acharModulo(this.router.url)));

    // Confere a saúde da API agora e depois a cada 20 segundos
    this.saude.atualizar();
    const relogio = setInterval(() => this.saude.atualizar(), 20000);
    this.destroyRef.onDestroy(() => clearInterval(relogio));
  }

  alterarSenha(): void {
    this.menuAberto.set(false);
    this.router.navigateByUrl('/trocar-senha');
  }

  sair(): void {
    this.auth.sair().subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: () => {
        // Mesmo se o servidor não responder, esquece o usuário aqui e volta para o login
        this.auth.limpar();
        this.router.navigateByUrl('/login');
      },
    });
  }

  private acharModulo(url: string): Modulo | undefined {
    return MODULOS.find((modulo) => url.startsWith('/' + modulo.caminho));
  }
}
