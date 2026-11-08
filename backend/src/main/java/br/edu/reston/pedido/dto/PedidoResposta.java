package br.edu.reston.pedido.dto;

import java.time.Instant;
import java.util.List;

import br.edu.reston.cardapio.DestinoPreparo;
import br.edu.reston.pedido.ItemPedido;
import br.edu.reston.pedido.Pedido;
import br.edu.reston.pedido.StatusPedido;

public record PedidoResposta(
        Long id,
        Long comandaId,
        String comandaCodigo,
        int numeroCliente,
        int mesaNumero,
        DestinoPreparo destino,
        StatusPedido status,
        Instant criadoEm,
        int tempoPrevistoMin,
        List<ItemResposta> itens) {

    public static PedidoResposta de(Pedido p) {
        // O pedido demora o que demora o item mais lento
        int tempoPrevisto = p.getItens().stream()
                .filter(i -> !i.isCancelado())
                .mapToInt(i -> i.getProduto().getTempoPreparoMin())
                .max().orElse(0);

        return new PedidoResposta(
                p.getId(), p.getComanda().getId(), p.getComanda().getCodigo(), p.getComanda().getNumeroCliente(),
                p.getComanda().getAtendimento().getMesa().getNumero(), p.getDestino(), p.getStatus(), p.getCriadoEm(),
                tempoPrevisto, p.getItens().stream().map(ItemResposta::de).toList());
    }
}
