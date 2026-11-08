package br.edu.reston.mesa.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MesaRequest(
        @NotNull(message = "Informe o número da mesa.")
        @Min(value = 1, message = "O número da mesa deve ser maior que zero.")
        @Max(value = 999, message = "O número da mesa deve ser no máximo 999.")
        Integer numero,

        @NotNull(message = "Informe quantos lugares a mesa tem.")
        @Min(value = 1, message = "A mesa precisa ter pelo menos 1 lugar.")
        @Max(value = 30, message = "A mesa pode ter no máximo 30 lugares.")
        Integer capacidade,

        @NotNull(message = "Informe se a mesa está ativa.")
        Boolean ativa,

        // Opcional: sem setor, a mesa vai para o Salão
        @Size(max = 30, message = "O setor pode ter no máximo 30 letras.")
        String setor) {

    public String setorOuPadrao() {
        return setor == null || setor.isBlank() ? "Salão" : setor.trim();
    }
}
