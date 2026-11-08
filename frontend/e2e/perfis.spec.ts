import { expect, test } from '@playwright/test';
import { entrar, menuLateral } from './auxiliar';

const perfis = [
  {
    login: 'gerente',
    url: /\/visao-geral$/,
    menu: ['Visão geral', 'Mesas', 'Reservas', 'Comandas', 'Cozinha', 'Caixa', 'Relatórios', 'Cardápio', 'Estoque', 'Equipe'],
    fora: ['Auditoria'],
  },
  { login: 'caixa', url: /\/caixa$/, menu: ['Comandas', 'Caixa'], fora: ['Mesas', 'Cozinha', 'Relatórios', 'Equipe'] },
  { login: 'cozinha', url: /\/cozinha$/, menu: ['Cozinha'], fora: ['Mesas', 'Caixa', 'Relatórios', 'Comandas'] },
];

for (const p of perfis) {
  test(`${p.login} entra na página certa e vê só o menu permitido`, async ({ page }) => {
    await entrar(page, p.login);
    await expect(page).toHaveURL(p.url);
    const menu = menuLateral(page);
    for (const nome of p.menu) {
      await expect(menu.getByRole('link', { name: nome, exact: true })).toBeVisible();
    }
    for (const nome of p.fora) {
      await expect(menu.getByRole('link', { name: nome, exact: true })).toHaveCount(0);
    }
  });
}

test('garcom1 entra em Mesas e vê o menu do garçom', async ({ page }) => {
  await entrar(page, 'garcom1');
  await expect(page).toHaveURL(/\/mesas$/);
  const menu = menuLateral(page);
  for (const nome of ['Mesas', 'Reservas', 'Comandas']) {
    await expect(menu.getByRole('link', { name: nome, exact: true })).toBeAttached();
  }
  for (const nome of ['Caixa', 'Relatórios', 'Cozinha', 'Estoque', 'Equipe']) {
    await expect(menu.getByRole('link', { name: nome, exact: true })).toHaveCount(0);
  }
});

test('bartender entra no Bar, sem filtros de cozinha e sem Mesas', async ({ page }) => {
  await entrar(page, 'bartender');
  await expect(page).toHaveURL(/\/cozinha$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Bar' })).toBeVisible();
  await expect(page.getByRole('button', { name: /^Cozinha/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /^Todos/ })).toHaveCount(0);
  const menu = menuLateral(page);
  await expect(menu.getByRole('link', { name: 'Cozinha', exact: true })).toBeVisible();
  await expect(menu.getByRole('link', { name: 'Mesas', exact: true })).toHaveCount(0);
  await expect(menu.getByRole('link', { name: 'Caixa', exact: true })).toHaveCount(0);
});
