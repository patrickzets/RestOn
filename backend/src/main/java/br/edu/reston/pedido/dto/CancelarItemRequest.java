package br.edu.reston.pedido.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarItemRequest(
        @NotBlank(message = "Informe o motivo do cancelamento.")
        @Size(min = 3, max = 120, message = "O motivo deve ter de 3 a 120 caracteres.")
        String motivo) {
}
