package br.edu.reston.caixa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaxaRequest(
        @NotNull(message = "Informe se a taxa deve ser removida.")
        Boolean removida,

        // Obrigatório ao remover (o serviço confere); ao devolver a taxa pode ficar em branco
        @Size(max = 120, message = "O motivo pode ter no máximo 120 caracteres.")
        String motivo) {
}
