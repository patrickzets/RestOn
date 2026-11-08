import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Categoria, Produto } from '../../servicos/cardapio.service';
import { Cardapio } from './cardapio';

const CATEGORIAS: Categoria[] = [
  { id: 1, nome: 'Pratos', ordem: 0, qtdProdutos: 2 },
  { id: 2, nome: 'Bebidas', ordem: 1, qtdProdutos: 1 },
];

const RISOTO: Produto = { id: 10, categoriaId: 1, categoriaNome: 'Pratos', nome: 'Risoto de funghi', descricao: 'Arroz cremoso', preco: 49, imagem: 'abc.png', tempoPreparoMin: 18, destinoPreparo: 'COZINHA', disponivel: true, ativo: true, controlaEstoque: false, estoqueAtual: 0, estoqueMinimo: 0 };
const SUCO: Produto = { id: 11, categoriaId: 2, categoriaNome: 'Bebidas', nome: 'Suco de laranja', descricao: null, preco: 8, imagem: null, tempoPreparoMin: 3, destinoPreparo: 'BAR', disponivel: false, ativo: true, controlaEstoque: false, estoqueAtual: 0, estoqueMinimo: 0 };
const MOQUECA: Produto = { id: 12, categoriaId: 1, categoriaNome: 'Pratos', nome: 'Moqueca', descricao: 'Peixe no leite de coco', preco: 74, imagem: null, tempoPreparoMin: 25, destinoPreparo: 'COZINHA', disponivel: true, ativo: false, controlaEstoque: false, estoqueAtual: 0, estoqueMinimo: 0 };
const PRODUTOS = [RISOTO, SUCO, MOQUECA];

describe('Cardapio', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Cardapio],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function abrir(produtos: Produto[] = PRODUTOS, categorias: Categoria[] = CATEGORIAS): ComponentFixture<Cardapio> {
    const fixture = TestBed.createComponent(Cardapio);
    fixture.detectChanges();
    http.expectOne('/api/v1/produtos').flush(produtos);
    http.expectOne('/api/v1/categorias').flush(categorias);
    fixture.detectChanges();
    return fixture;
  }

  function tela(fixture: ComponentFixture<Cardapio>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function botao(fixture: ComponentFixture<Cardapio>, texto: string): HTMLButtonElement {
    const botoes = Array.from(tela(fixture).querySelectorAll('button')) as HTMLButtonElement[];
    return botoes.find((b) => b.textContent?.trim().startsWith(texto))!;
  }

  function digitar(fixture: ComponentFixture<Cardapio>, id: string, valor: string): void {
    const campo = tela(fixture).querySelector('#' + id) as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  }

  it('mostra os produtos com preço em reais e esconde os arquivados', () => {
    const texto = tela(abrir()).textContent!.replace(/ /g, ' ');

    expect(texto).toContain('Risoto de funghi');
    expect(texto).toContain('R$ 49,00');
    expect(texto).toContain('Suco de laranja');
    expect(texto).toContain('Indisponível');
    expect(texto).not.toContain('Moqueca');
  });

  it('a foto é pedida com a versão do arquivo e produto sem foto mostra um espaço vazio', () => {
    const fixture = abrir();
    const imagens = tela(fixture).querySelectorAll('.produto img');

    expect(imagens.length).toBe(1);
    expect(imagens[0].getAttribute('src')).toBe('/api/v1/produtos/10/imagem?v=abc.png');
  });

  it('mostra os arquivados só quando o filtro é ligado', () => {
    const fixture = abrir();
    const caixa = tela(fixture).querySelector('.marcar input') as HTMLInputElement;
    caixa.checked = true;
    caixa.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(tela(fixture).textContent).toContain('Moqueca');
    expect(tela(fixture).textContent).toContain('Arquivado');
  });

  it('filtra por categoria e pela busca', () => {
    const fixture = abrir();
    botao(fixture, 'Bebidas').click();
    fixture.detectChanges();
    expect(tela(fixture).querySelectorAll('.produto').length).toBe(1);
    expect(tela(fixture).textContent).toContain('Suco de laranja');

    botao(fixture, 'Todos').click();
    const busca = tela(fixture).querySelector('input[type="search"]') as HTMLInputElement;
    busca.value = 'risoto';
    busca.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(tela(fixture).querySelectorAll('.produto').length).toBe(1);
  });

  it('a chave do cartão liga ou desliga a venda sem abrir a gaveta', () => {
    const fixture = abrir();
    (tela(fixture).querySelector('.produto .chave') as HTMLButtonElement).click();

    const chamada = http.expectOne((r) => r.method === 'PATCH' && r.url === '/api/v1/produtos/10/disponibilidade');
    expect(chamada.request.body).toEqual({ disponivel: false });
    chamada.flush({ ...RISOTO, disponivel: false });
    http.expectOne('/api/v1/categorias').flush(CATEGORIAS);
    fixture.detectChanges();

    expect(tela(fixture).querySelector('.gaveta')).toBeNull();
    expect(tela(fixture).textContent).toContain('Indisponível');
  });

  it('não deixa criar produto com campos vazios', () => {
    const fixture = abrir();
    botao(fixture, 'Novo produto').click();
    fixture.detectChanges();

    (tela(fixture).querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    http.expectNone((r) => r.method === 'POST');
    expect(tela(fixture).textContent).toContain('Informe o nome');
    expect(tela(fixture).textContent).toContain('maior que zero');
  });

  it('cria o produto e depois envia a foto escolhida', () => {
    const fixture = abrir();
    botao(fixture, 'Novo produto').click();
    fixture.detectChanges();

    const foto = new File([new Uint8Array([1, 2, 3])], 'pudim.png', { type: 'image/png' });
    const campoDaFoto = tela(fixture).querySelector('#arquivoFoto') as HTMLInputElement;
    Object.defineProperty(campoDaFoto, 'files', { value: [foto], configurable: true });
    campoDaFoto.dispatchEvent(new Event('change'));

    digitar(fixture, 'nome', 'Pudim de leite');
    digitar(fixture, 'preco', '16');
    (tela(fixture).querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const criacao = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/produtos');
    expect(criacao.request.body).toMatchObject({ nome: 'Pudim de leite', preco: 16, destinoPreparo: 'COZINHA', categoriaId: 1 });
    criacao.flush({ ...RISOTO, id: 20, nome: 'Pudim de leite', imagem: null });

    const envio = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/produtos/20/imagem');
    expect(envio.request.body instanceof FormData).toBe(true);
    envio.flush({ ...RISOTO, id: 20, imagem: 'novo.png' });

    http.expectOne('/api/v1/produtos').flush(PRODUTOS);
    http.expectOne('/api/v1/categorias').flush(CATEGORIAS);
  });

  it('recusa uma foto grande demais sem chamar a API', () => {
    const fixture = abrir();
    botao(fixture, 'Novo produto').click();
    fixture.detectChanges();

    const enorme = new File([new Uint8Array(6 * 1024 * 1024)], 'grande.jpg', { type: 'image/jpeg' });
    const campoDaFoto = tela(fixture).querySelector('#arquivoFoto') as HTMLInputElement;
    Object.defineProperty(campoDaFoto, 'files', { value: [enorme], configurable: true });
    campoDaFoto.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(tela(fixture).querySelector('[role="alert"]')?.textContent).toContain('no máximo 5 MB');
    http.expectNone((r) => r.url.includes('/imagem'));
  });

  it('recusa um arquivo que não é JPG, PNG ou WebP', () => {
    const fixture = abrir();
    botao(fixture, 'Novo produto').click();
    fixture.detectChanges();

    const pdf = new File(['x'], 'cardapio.pdf', { type: 'application/pdf' });
    const campoDaFoto = tela(fixture).querySelector('#arquivoFoto') as HTMLInputElement;
    Object.defineProperty(campoDaFoto, 'files', { value: [pdf], configurable: true });
    campoDaFoto.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(tela(fixture).querySelector('[role="alert"]')?.textContent).toContain('JPG, PNG ou WebP');
  });

  it('pede confirmação antes de arquivar um produto', () => {
    const fixture = abrir();
    (tela(fixture).querySelector('.produto') as HTMLElement).click();
    fixture.detectChanges();

    botao(fixture, 'Arquivar produto').click();
    fixture.detectChanges();
    http.expectNone((r) => r.method === 'PATCH');

    botao(fixture, 'Arquivar').click();
    const chamada = http.expectOne((r) => r.method === 'PATCH' && r.url === '/api/v1/produtos/10/ativo');
    expect(chamada.request.body).toEqual({ ativo: false });
    chamada.flush({ ...RISOTO, ativo: false });
    http.expectOne('/api/v1/categorias').flush(CATEGORIAS);
  });

  it('sem categorias, explica o primeiro passo e não deixa criar produto', () => {
    const fixture = abrir([], []);

    expect(tela(fixture).textContent).toContain('Comece criando uma categoria');
    expect(botao(fixture, 'Novo produto').disabled).toBe(true);
  });

  it('adiciona uma categoria pelo painel de categorias', () => {
    const fixture = abrir();
    botao(fixture, 'Categorias').click();
    fixture.detectChanges();

    digitar(fixture, 'novaCategoria', 'Sobremesas');
    botao(fixture, 'Adicionar').click();

    const chamada = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/categorias');
    expect(chamada.request.body).toEqual({ nome: 'Sobremesas' });
    chamada.flush({ id: 3, nome: 'Sobremesas', ordem: 2, qtdProdutos: 0 });
    http.expectOne('/api/v1/produtos').flush(PRODUTOS);
    http.expectOne('/api/v1/categorias').flush(CATEGORIAS);
  });

  it('mostra o erro da API ao tentar excluir uma categoria com produtos', () => {
    const fixture = abrir();
    botao(fixture, 'Categorias').click();
    fixture.detectChanges();

    botao(fixture, 'Excluir').click();
    fixture.detectChanges();
    botao(fixture, 'Sim, excluir').click();
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/v1/categorias/1')
      .flush({ detail: 'Esta categoria tem produtos.' }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();

    expect(tela(fixture).querySelector('[role="alert"]')?.textContent).toContain('tem produtos');
  });
});
