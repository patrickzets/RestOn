package br.edu.reston.pedido.dto;

import jakarta.validation.constraints.NotNull;

public record TransferirItemRequest(
        @NotNull(message = "Escolha a comanda de destino.")
        Long comandaDestinoId) {
}
