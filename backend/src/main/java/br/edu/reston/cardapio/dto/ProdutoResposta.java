package br.edu.reston.cardapio.dto;

import java.math.BigDecimal;

import br.edu.reston.cardapio.DestinoPreparo;
import br.edu.reston.cardapio.Produto;

public record ProdutoResposta(
        Long id,
        Long categoriaId,
        String categoriaNome,
        String nome,
        String descricao,
        BigDecimal preco,
        String imagem, // nome do arquivo; serve só para a tela saber se tem foto e quando ela mudou
        int tempoPreparoMin,
        DestinoPreparo destinoPreparo,
        boolean disponivel,
        boolean ativo,
        boolean controlaEstoque,
        int estoqueAtual,
        int estoqueMinimo) {

    public static ProdutoResposta de(Produto p) {
        return new ProdutoResposta(p.getId(), p.getCategoria().getId(), p.getCategoria().getNome(), p.getNome(),
                p.getDescricao(), p.getPreco(), p.getImagem(), p.getTempoPreparoMin(), p.getDestinoPreparo(),
                p.isDisponivel(), p.isAtivo(), p.isControlaEstoque(), p.getEstoqueAtual(), p.getEstoqueMinimo());
    }
}
