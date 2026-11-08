package br.edu.reston.pedido.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record NovoPedidoRequest(
        @NotEmpty(message = "Escolha pelo menos um item.")
        @Size(max = 60, message = "Um envio pode ter no máximo 60 itens.")
        List<@Valid ItemRequest> itens) {
}
