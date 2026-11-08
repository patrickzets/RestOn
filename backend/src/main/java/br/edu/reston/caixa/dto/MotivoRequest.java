package br.edu.reston.caixa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Usado no estorno: o motivo é sempre obrigatório
public record MotivoRequest(
        @NotBlank(message = "Informe o motivo.")
        @Size(min = 3, max = 120, message = "O motivo deve ter de 3 a 120 caracteres.")
        String motivo) {
}
