package br.edu.reston.cardapio.dto;

import br.edu.reston.cardapio.Categoria;

public record CategoriaResposta(Long id, String nome, int ordem, long qtdProdutos) {

    public static CategoriaResposta de(Categoria c, long qtdProdutos) {
        return new CategoriaResposta(c.getId(), c.getNome(), c.getOrdem(), qtdProdutos);
    }
}
