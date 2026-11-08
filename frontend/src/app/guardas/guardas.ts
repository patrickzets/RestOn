import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService, Perfil } from '../servicos/auth.service';

// O inject() precisa vir antes do primeiro await
export const autenticadoGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const usuario = await auth.carregar();
  if (!usuario) return router.createUrlTree(['/login']);
  if (usuario.senhaProvisoria) return router.createUrlTree(['/trocar-senha']);
  return true;
};

export const logadoGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const usuario = await auth.carregar();
  return usuario ? true : router.createUrlTree(['/login']);
};

export const visitanteGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const usuario = await auth.carregar();
  if (!usuario) return true;
  return router.createUrlTree([usuario.senhaProvisoria ? '/trocar-senha' : auth.paginaInicial(usuario.perfil)]);
};

export const perfilGuard: CanActivateFn = (rota) => {
  const auth = inject(AuthService);
  const usuario = auth.usuario();
  const permitidos = rota.data['perfis'] as Perfil[] | undefined;

  if (!usuario) return inject(Router).createUrlTree(['/login']);
  if (permitidos && !permitidos.includes(usuario.perfil)) {
    return inject(Router).createUrlTree([auth.paginaInicial(usuario.perfil)]);
  }
  return true;
};

export const inicialGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const usuario = auth.usuario();
  return inject(Router).createUrlTree([usuario ? auth.paginaInicial(usuario.perfil) : '/login']);
};
