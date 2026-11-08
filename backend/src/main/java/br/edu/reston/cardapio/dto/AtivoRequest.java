package br.edu.reston.cardapio.dto;

import jakarta.validation.constraints.NotNull;

public record AtivoRequest(
        @NotNull(message = "Informe se o produto está ativo.")
        Boolean ativo) {
}
