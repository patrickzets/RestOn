package br.edu.reston.cardapio.dto;

import jakarta.validation.constraints.NotNull;

public record DisponibilidadeRequest(
        @NotNull(message = "Informe se o produto está disponível.")
        Boolean disponivel) {
}
