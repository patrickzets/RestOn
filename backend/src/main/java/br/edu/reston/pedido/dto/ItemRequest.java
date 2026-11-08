package br.edu.reston.pedido.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ItemRequest(
        @NotNull(message = "Escolha o produto.")
        Long produtoId,

        @NotNull(message = "Informe a quantidade.")
        @Min(value = 1, message = "A quantidade mínima é 1.")
        @Max(value = 50, message = "A quantidade máxima por item é 50.")
        Integer quantidade,

        @Size(max = 80, message = "A observação pode ter no máximo 80 caracteres.")
        String observacao) {
}
