package br.edu.reston.caixa.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record FecharTurnoRequest(
        // O dinheiro que a pessoa contou na gaveta ao fechar
        @NotNull(message = "Informe quanto você contou em dinheiro no caixa.")
        @DecimalMin(value = "0.00", message = "O valor contado não pode ser negativo.")
        @DecimalMax(value = "999999.99", message = "O valor contado é grande demais.")
        @Digits(integer = 6, fraction = 2, message = "Use no máximo 2 casas decimais.")
        BigDecimal valorContado) {
}
