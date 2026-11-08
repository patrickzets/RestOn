package br.edu.reston.estoque.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AjusteRequest(
        @NotNull(message = "Informe a quantidade contada.")
        @Min(value = 0, message = "A quantidade não pode ser negativa.")
        @Max(value = 100000, message = "A quantidade deve ser de no máximo 100.000.")
        Integer quantidade,

        @NotBlank(message = "Explique o motivo do ajuste.")
        @Size(min = 3, max = 120, message = "O motivo deve ter de 3 a 120 caracteres.")
        String motivo) {
}
