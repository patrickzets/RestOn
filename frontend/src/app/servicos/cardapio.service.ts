import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type DestinoPreparo = 'COZINHA' | 'BAR' | 'SEM_PREPARO';

export const NOMES_DOS_DESTINOS: Record<DestinoPreparo, string> = {
  COZINHA: 'Cozinha',
  BAR: 'Bar',
  SEM_PREPARO: 'Sem preparo',
};

export interface Categoria {
  id: number;
  nome: string;
  ordem: number;
  qtdProdutos: number;
}

export interface Produto {
  id: number;
  categoriaId: number;
  categoriaNome: string;
  nome: string;
  descricao: string | null;
  preco: number;
  imagem: string | null; // nome do arquivo; muda quando a foto é trocada
  tempoPreparoMin: number;
  destinoPreparo: DestinoPreparo;
  disponivel: boolean;
  ativo: boolean;
  controlaEstoque: boolean;
  estoqueAtual: number;
  estoqueMinimo: number;
}

export interface DadosDoProduto {
  categoriaId: number;
  nome: string;
  descricao: string;
  preco: number;
  tempoPreparoMin: number;
  destinoPreparo: DestinoPreparo;
}

@Injectable({ providedIn: 'root' })
export class CardapioService {
  private http = inject(HttpClient);
  private produtos = '/api/v1/produtos';
  private categorias = '/api/v1/categorias';

  listarProdutos(): Observable<Produto[]> {
    return this.http.get<Produto[]>(this.produtos);
  }

  criarProduto(dados: DadosDoProduto): Observable<Produto> {
    return this.http.post<Produto>(this.produtos, dados);
  }

  atualizarProduto(id: number, dados: DadosDoProduto): Observable<Produto> {
    return this.http.put<Produto>(`${this.produtos}/${id}`, dados);
  }

  mudarDisponibilidade(id: number, disponivel: boolean): Observable<Produto> {
    return this.http.patch<Produto>(`${this.produtos}/${id}/disponibilidade`, { disponivel });
  }

  mudarAtivo(id: number, ativo: boolean): Observable<Produto> {
    return this.http.patch<Produto>(`${this.produtos}/${id}/ativo`, { ativo });
  }

  enviarImagem(id: number, arquivo: File): Observable<Produto> {
    const dados = new FormData();
    dados.append('arquivo', arquivo);
    return this.http.post<Produto>(`${this.produtos}/${id}/imagem`, dados);
  }

  removerImagem(id: number): Observable<Produto> {
    return this.http.delete<Produto>(`${this.produtos}/${id}/imagem`);
  }

  // O "?v=" muda quando a foto muda, então o navegador nunca mostra a foto antiga
  urlDaImagem(produto: Produto): string | null {
    return produto.imagem ? `${this.produtos}/${produto.id}/imagem?v=${produto.imagem}` : null;
  }

  listarCategorias(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(this.categorias);
  }

  criarCategoria(nome: string): Observable<Categoria> {
    return this.http.post<Categoria>(this.categorias, { nome });
  }

  renomearCategoria(id: number, nome: string): Observable<Categoria> {
    return this.http.put<Categoria>(`${this.categorias}/${id}`, { nome });
  }

  moverCategoria(id: number, direcao: 'subir' | 'descer'): Observable<Categoria[]> {
    return this.http.post<Categoria[]>(`${this.categorias}/${id}/${direcao}`, {});
  }

  excluirCategoria(id: number): Observable<void> {
    return this.http.delete<void>(`${this.categorias}/${id}`);
  }
}
