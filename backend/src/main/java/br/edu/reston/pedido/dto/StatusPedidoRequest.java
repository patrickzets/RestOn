package br.edu.reston.pedido.dto;

import br.edu.reston.pedido.StatusPedido;
import jakarta.validation.constraints.NotNull;

public record StatusPedidoRequest(
        @NotNull(message = "Informe o novo status.")
        StatusPedido status) {
}
