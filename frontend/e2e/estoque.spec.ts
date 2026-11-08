import { expect, test } from '@playwright/test';
import { entrar } from './auxiliar';

test('produto esgotado aparece como Esgotado e não pode ser adicionado', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await entrar(page, 'garcom2');
  await page.goto('/mesas');
  // no demo a mesa 4 está ocupada com a comanda da Família Souza
  await page.locator('button.mesa', { hasText: 'Mesa 4' }).click();
  await page.getByRole('link', { name: 'Pedido', exact: true }).first().click();
  await expect(page).toHaveURL(/\/comandas\/\d+\/pedido$/);

  await page.getByRole('searchbox', { name: 'Buscar produto' }).fill('Guaraná');
  const item = page.locator('li.produto', { hasText: 'Guaraná Lata' });
  await expect(item.getByText('Esgotado')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Adicionar Guaraná Lata' })).toBeDisabled();

  await page.getByRole('searchbox', { name: 'Buscar produto' }).fill('Refrigerante');
  await expect(page.getByRole('button', { name: 'Adicionar Refrigerante Lata' })).toBeEnabled();
});
