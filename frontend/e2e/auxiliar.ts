import { expect, Page } from '@playwright/test';

export function senhaDemo(): string {
  const senha = process.env['RESTON_DEMO_SENHA'];
  if (!senha) {
    throw new Error(
      'Defina a variável de ambiente RESTON_DEMO_SENHA com a senha dos usuários de demonstração (constante SENHA_DEMO em backend/.../demo/DadosDemo.java).',
    );
  }
  return senha;
}

export async function entrar(pagina: Page, login: string): Promise<void> {
  await pagina.goto('/login');
  await pagina.getByLabel('Login', { exact: true }).fill(login);
  await pagina.getByLabel('Senha', { exact: true }).fill(senhaDemo());
  await pagina.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(pagina).not.toHaveURL(/\/login/);
}

export function menuLateral(pagina: Page) {
  return pagina.locator('aside.lateral');
}

// "R$ 1.234,56" vira 1234.56
export function lerValor(texto: string): number {
  const numeros = texto.replace(/[^\d,]/g, '').replace(',', '.');
  return Number(numeros);
}
