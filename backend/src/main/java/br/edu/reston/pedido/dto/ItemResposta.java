package br.edu.reston.pedido.dto;

import java.math.BigDecimal;

import br.edu.reston.pedido.ItemPedido;

public record ItemResposta(
        Long id,
        Long produtoId,
        String produtoNome,
        int quantidade,
        BigDecimal precoUnitario,
        BigDecimal subtotal,
        String observacao,
        boolean cancelado,
        String motivoCancelamento,
        Long comandaId) {

    public static ItemResposta de(ItemPedido i) {
        return new ItemResposta(i.getId(), i.getProduto().getId(), i.getProduto().getNome(), i.getQuantidade(),
                i.getPrecoUnitario(), i.subtotal(), i.getObservacao(), i.isCancelado(), i.getMotivoCancelamento(),
                i.getComanda().getId());
    }
}
