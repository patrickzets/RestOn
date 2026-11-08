package br.edu.reston.atendimento.dto;

import jakarta.validation.constraints.NotNull;

public record TrocarMesaRequest(
        @NotNull(message = "Escolha a mesa de destino.")
        Long novaMesaId) {
}
