package br.edu.reston.estoque.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EntradaRequest(
        @NotNull(message = "Informe a quantidade.")
        @Min(value = 1, message = "A quantidade mínima é 1.")
        @Max(value = 100000, message = "A quantidade deve ser de no máximo 100.000.")
        Integer quantidade,

        @Size(max = 120, message = "A observação pode ter no máximo 120 caracteres.")
        String observacao) {
}
