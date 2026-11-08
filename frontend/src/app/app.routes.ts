import { Type } from '@angular/core';
import { Routes } from '@angular/router';

import { autenticadoGuard, inicialGuard, logadoGuard, perfilGuard, visitanteGuard } from './guardas/guardas';
import { LayoutPrincipal } from './layout/layout-principal';
import { MODULOS } from './modulos';
import { Login } from './paginas/login/login';
import { TrocarSenha } from './paginas/trocar-senha/trocar-senha';

// Cada página só é baixada quando alguém abre (assim o primeiro carregamento fica leve)
type Carregador = () => Promise<Type<unknown>>;

// Páginas já construídas. Os outros módulos mostram "em construção" até chegar a vez deles.
const PAGINAS_PRONTAS: Record<string, Carregador> = {
  'visao-geral': () => import('./paginas/visao-geral/visao-geral').then((m) => m.VisaoGeral),
  cardapio: () => import('./paginas/cardapio/cardapio').then((m) => m.Cardapio),
  caixa: () => import('./paginas/caixa/caixa').then((m) => m.Caixa),
  comandas: () => import('./paginas/comandas/comandas').then((m) => m.Comandas),
  cozinha: () => import('./paginas/cozinha/cozinha').then((m) => m.Cozinha),
  mesas: () => import('./paginas/mesas/mesas').then((m) => m.Mesas),
  reservas: () => import('./paginas/reservas/reservas').then((m) => m.Reservas),
  estoque: () => import('./paginas/estoque/estoque').then((m) => m.Estoque),
  equipe: () => import('./paginas/equipe/equipe').then((m) => m.Equipe),
  auditoria: () => import('./paginas/auditoria/auditoria').then((m) => m.Auditoria),
  relatorios: () => import('./paginas/relatorios/relatorios').then((m) => m.Relatorios),
};

const EM_CONSTRUCAO: Carregador = () => import('./paginas/em-construcao/em-construcao').then((m) => m.EmConstrucao);

const rotasDosModulos = MODULOS.map((modulo) => ({
  path: modulo.caminho,
  loadComponent: PAGINAS_PRONTAS[modulo.caminho] ?? EM_CONSTRUCAO,
  canActivate: [perfilGuard],
  data: { modulo, perfis: modulo.perfis },
}));

export const routes: Routes = [
  { path: 'login', component: Login, canActivate: [visitanteGuard] },
  { path: 'trocar-senha', component: TrocarSenha, canActivate: [logadoGuard] },
  {
    path: '',
    component: LayoutPrincipal,
    canActivate: [autenticadoGuard],
    children: [
      // A raiz manda cada perfil para a sua página inicial
      { path: '', pathMatch: 'full', canActivate: [inicialGuard], children: [] },
      ...rotasDosModulos,
      // O comprovante vale para a conta parcial (garçom) e para a conta paga (caixa)
      {
        path: 'comandas/:id/comprovante',
        loadComponent: () => import('./paginas/comprovante/comprovante').then((m) => m.Comprovante),
        canActivate: [perfilGuard],
        data: { perfis: ['ADMIN', 'GERENTE', 'CAIXA', 'GARCOM'] },
      },
      // Tela de lançar pedido (feita para o celular do garçom)
      {
        path: 'comandas/:id/pedido',
        loadComponent: () => import('./paginas/pedido-novo/pedido-novo').then((m) => m.PedidoNovo),
        canActivate: [perfilGuard],
        data: { perfis: ['ADMIN', 'GERENTE', 'GARCOM'] },
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
