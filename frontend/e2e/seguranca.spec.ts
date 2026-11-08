import { expect, test } from '@playwright/test';
import { entrar, menuLateral } from './auxiliar';

test('garçom é redirecionado ao tentar abrir Caixa e Relatórios pela URL', async ({ page }) => {
  await entrar(page, 'garcom1');
  await page.goto('/caixa');
  await expect(page).toHaveURL(/\/mesas$/);
  await page.goto('/relatorios');
  await expect(page).toHaveURL(/\/mesas$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Mesas' })).toBeVisible();
});

test('bartender não vê Mesas e é redirecionado ao abrir /mesas', async ({ page }) => {
  await entrar(page, 'bartender');
  await expect(menuLateral(page).getByRole('link', { name: 'Mesas', exact: true })).toHaveCount(0);
  await page.goto('/mesas');
  await expect(page).toHaveURL(/\/cozinha$/);
});

test('sem login, qualquer página manda para o login', async ({ page }) => {
  await page.goto('/caixa');
  await expect(page).toHaveURL(/\/login$/);
});
