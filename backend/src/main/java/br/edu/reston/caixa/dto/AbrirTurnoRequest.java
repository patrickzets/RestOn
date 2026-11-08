package br.edu.reston.caixa.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record AbrirTurnoRequest(
        @NotNull(message = "Informe o valor inicial do caixa (pode ser zero).")
        @DecimalMin(value = "0.00", message = "O valor inicial não pode ser negativo.")
        @DecimalMax(value = "99999.99", message = "O valor inicial deve ser no máximo R$ 99.999,99.")
        @Digits(integer = 5, fraction = 2, message = "Use no máximo 2 casas decimais.")
        BigDecimal valorInicial) {
}
