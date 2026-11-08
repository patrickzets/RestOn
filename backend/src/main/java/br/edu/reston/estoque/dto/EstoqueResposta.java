package br.edu.reston.estoque.dto;

import br.edu.reston.cardapio.Produto;

public record EstoqueResposta(
        Long produtoId,
        String nome,
        String categoriaNome,
        boolean ativo,
        boolean controla,
        int quantidade,
        int minimo,
        String situacao) { // OK, BAIXO ou ZERADO (nulo quando o produto não tem controle)

    public static EstoqueResposta de(Produto p) {
        String situacao = null;
        if (p.isControlaEstoque()) {
            situacao = p.getEstoqueAtual() == 0 ? "ZERADO" : p.getEstoqueAtual() <= p.getEstoqueMinimo() ? "BAIXO" : "OK";
        }
        return new EstoqueResposta(p.getId(), p.getNome(), p.getCategoria().getNome(), p.isAtivo(), p.isControlaEstoque(),
                p.getEstoqueAtual(), p.getEstoqueMinimo(), situacao);
    }
}
