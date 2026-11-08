package br.edu.reston.reserva.dto;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReservaRequest(
        @NotNull(message = "Escolha a mesa.")
        Long mesaId,

        @NotBlank(message = "Informe o nome do cliente.")
        @Size(min = 2, max = 120, message = "O nome deve ter de 2 a 120 caracteres.")
        String nomeCliente,

        // Pode vir vazio ao editar (quem não vê o telefone não consegue reenviá-lo); ao criar é obrigatório
        @Pattern(regexp = "^\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}$", message = "Telefone inválido. Use o DDD e o número, como (11) 91234-5678.")
        String telefone,

        @NotNull(message = "Informe quantas pessoas virão.")
        @Min(value = 1, message = "A reserva precisa ter pelo menos 1 pessoa.")
        @Max(value = 30, message = "A reserva pode ter no máximo 30 pessoas.")
        Integer qtdPessoas,

        @NotNull(message = "Informe a data e o horário.")
        Instant inicio) {
}
