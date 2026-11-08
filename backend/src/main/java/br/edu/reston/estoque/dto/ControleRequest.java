package br.edu.reston.estoque.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ControleRequest(
        @NotNull(message = "Informe se o produto tem controle de estoque.")
        Boolean controla,

        @NotNull(message = "Informe o estoque mínimo.")
        @Min(value = 0, message = "O estoque mínimo não pode ser negativo.")
        @Max(value = 100000, message = "O estoque mínimo deve ser de no máximo 100.000.")
        Integer minimo,

        // Só vale ao ligar o controle
        @Min(value = 0, message = "A quantidade inicial não pode ser negativa.")
        @Max(value = 100000, message = "A quantidade deve ser de no máximo 100.000.")
        Integer quantidadeInicial) {
}
