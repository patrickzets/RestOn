package br.edu.reston.caixa.dto;

import java.math.BigDecimal;

import br.edu.reston.caixa.MetodoPagamento;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record AnteciparRequest(
        @NotNull(message = "Escolha a forma de pagamento.")
        MetodoPagamento metodo,

        @NotNull(message = "Informe o valor.")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
        @DecimalMax(value = "99999.99", message = "O valor deve ser no máximo R$ 99.999,99.")
        @Digits(integer = 5, fraction = 2, message = "Use no máximo 2 casas decimais.")
        BigDecimal valor) {
}
