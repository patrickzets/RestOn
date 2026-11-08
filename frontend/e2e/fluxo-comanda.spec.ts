import { Browser, expect, Page, test } from '@playwright/test';
import { entrar, lerValor } from './auxiliar';

const PRATO = 'Salada Caesar com Frango';
const DRINQUE = 'Caipirinha de Limão';

async function novaPagina(browser: Browser, login: string, celular = false): Promise<Page> {
  const contexto = await browser.newContext(
    celular ? { viewport: { width: 390, height: 844 }, hasTouch: true } : { viewport: { width: 1366, height: 800 } },
  );
  const pagina = await contexto.newPage();
  await entrar(pagina, login);
  return pagina;
}

async function faturamentoDeHoje(pagina: Page): Promise<number> {
  await pagina.goto('/visao-geral');
  const valor = pagina.locator('.indicador', { hasText: 'Faturamento de hoje' }).locator('strong');
  await expect(valor).toContainText('R$');
  return lerValor(await valor.innerText());
}

async function adicionar(pagina: Page, produto: string) {
  await pagina.getByRole('searchbox', { name: 'Buscar produto' }).fill(produto);
  await pagina.getByRole('button', { name: `Adicionar ${produto}` }).click();
}

// O demo já tem outros pedidos na cozinha e no bar, então o ticket é achado pela mesa e pelo produto
function ticketDe(pagina: Page, mesa: string, produto: string) {
  return pagina.locator('article.ticket', { hasText: new RegExp(`Mesa ${mesa}(?!\\d)`) }).filter({ hasText: produto });
}

// Leva o pedido da coluna Recebido até Pronto na tela da cozinha (ou do bar)
async function prepararAtePronto(pagina: Page, mesa: string, produto: string) {
  const ticket = ticketDe(pagina, mesa, produto);
  await ticket.getByRole('button', { name: 'Iniciar preparo' }).click();
  await ticket.getByRole('button', { name: 'Marcar como pronto' }).click();
  await expect(ticket.getByText('Pronto · aguardando o garçom')).toBeVisible();
}

test('do pedido do garçom ao faturamento do gerente', async ({ browser }) => {
  const gerente = await novaPagina(browser, 'gerente');
  const antes = await faturamentoDeHoje(gerente);

  const garcom = await novaPagina(browser, 'garcom1', true);
  const cozinha = await novaPagina(browser, 'cozinha');
  const bar = await novaPagina(browser, 'bartender');
  const caixa = await novaPagina(browser, 'caixa');

  // cozinha e bar ficam abertos sem recarregar, para provar o tempo real (SSE)
  await expect(cozinha.getByRole('heading', { level: 1, name: 'Cozinha' })).toBeVisible();
  await expect(bar.getByRole('heading', { level: 1, name: 'Bar' })).toBeVisible();

  // garçom abre o atendimento numa mesa livre (a primeira comanda já nasce junto)
  await garcom.goto('/mesas');
  const mesaLivre = garcom.locator('button.mesa', { has: garcom.getByText('Livre', { exact: true }) }).first();
  await expect(mesaLivre).toBeVisible();
  const numeroDaMesa = (await mesaLivre.locator('.numero').innerText()).replace('Mesa ', '').trim();
  await mesaLivre.click();
  await garcom.getByRole('button', { name: 'Abrir atendimento' }).click();
  await garcom.getByRole('link', { name: 'Pedido', exact: true }).click();
  await expect(garcom).toHaveURL(/\/comandas\/\d+\/pedido$/);
  const codigoDaComanda = (await garcom.locator('header.cabecalho p').innerText()).split('·')[0].trim();

  await adicionar(garcom, PRATO);
  await adicionar(garcom, DRINQUE);
  await garcom.getByRole('searchbox', { name: 'Buscar produto' }).fill('');
  await garcom.getByRole('button', { name: /^Ver pedido/ }).click();
  await expect(garcom.getByText('Será enviado em 2 pedidos')).toBeVisible();
  await garcom.getByRole('button', { name: 'Enviar pedido' }).click();
  await expect(garcom).toHaveURL(/\/mesas$/);

  // a cozinha vê o prato chegar sozinha, e o bar vê o drinque
  await expect(ticketDe(cozinha, numeroDaMesa, PRATO)).toBeVisible({ timeout: 15_000 });
  await prepararAtePronto(cozinha, numeroDaMesa, PRATO);

  await expect(ticketDe(bar, numeroDaMesa, DRINQUE)).toBeVisible({ timeout: 15_000 });
  await prepararAtePronto(bar, numeroDaMesa, DRINQUE);

  // garçom entrega os dois pelo painel da mesa
  await garcom.reload();
  await garcom.locator('button.mesa', { hasText: `Mesa ${numeroDaMesa}` }).first().click();
  const avisos = garcom.getByRole('status').filter({ hasText: 'Pronto para entregar' });
  await expect(avisos).toHaveCount(2);
  await avisos.first().getByRole('button', { name: 'Entreguei' }).click();
  await expect(avisos).toHaveCount(1);
  await avisos.first().getByRole('button', { name: 'Entreguei' }).click();
  await expect(avisos).toHaveCount(0);

  // caixa cobra em PIX o valor sugerido e fecha a comanda
  await caixa.goto('/caixa');
  await caixa.getByRole('searchbox', { name: 'Buscar comanda' }).fill(codigoDaComanda);
  await caixa.getByRole('row', { name: new RegExp(codigoDaComanda) }).click();
  const painel = caixa.getByRole('complementary', { name: 'Cobrança da comanda' });
  await painel.getByRole('radio', { name: 'PIX' }).check();
  const valorSugerido = await painel.getByLabel('Valor a receber (R$)').inputValue();
  expect(Number(valorSugerido)).toBeGreaterThan(0);
  await painel.getByRole('button', { name: 'Receber pagamento' }).click();
  await expect(painel.getByText('A comanda está paga.')).toBeVisible();
  await painel.getByRole('button', { name: 'Fechar comanda' }).click();
  await painel.getByRole('alertdialog').getByRole('button', { name: 'Fechar comanda' }).click();
  await expect(painel.getByText('Comanda fechada.')).toBeVisible();

  // o gerente vê o faturamento maior
  const depois = await faturamentoDeHoje(gerente);
  expect(depois).toBeGreaterThan(antes);
  expect(depois - antes).toBeCloseTo(Number(valorSugerido), 2);

  await gerente.goto('/relatorios');
  await gerente.getByRole('button', { name: 'Hoje' }).click();
  await gerente.getByRole('button', { name: 'Aplicar' }).click();
  const tile = gerente.locator('.tile', { hasText: 'Faturamento' }).first().locator('strong');
  await expect(tile).toContainText('R$');
  expect(lerValor(await tile.innerText())).toBeCloseTo(depois, 2);
  await expect(gerente.getByRole('row', { name: /Marcos Oliveira/ })).toBeVisible();

  for (const p of [gerente, garcom, cozinha, bar, caixa]) await p.context().close();
});
