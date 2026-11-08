package br.edu.reston.cardapio.dto;

import java.math.BigDecimal;

import br.edu.reston.cardapio.DestinoPreparo;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProdutoRequest(
        @NotNull(message = "Escolha a categoria.")
        Long categoriaId,

        @NotBlank(message = "Informe o nome do produto.")
        @Size(min = 2, max = 100, message = "O nome deve ter de 2 a 100 caracteres.")
        String nome,

        @Size(max = 160, message = "A descrição pode ter no máximo 160 caracteres.")
        String descricao,

        @NotNull(message = "Informe o preço.")
        @DecimalMin(value = "0.01", message = "O preço deve ser maior que zero.")
        @DecimalMax(value = "99999.99", message = "O preço deve ser no máximo R$ 99.999,99.")
        @Digits(integer = 5, fraction = 2, message = "Use no máximo 2 casas decimais no preço.")
        BigDecimal preco,

        @NotNull(message = "Informe o tempo de preparo.")
        @Min(value = 0, message = "O tempo de preparo não pode ser negativo.")
        @Max(value = 240, message = "O tempo de preparo deve ser de no máximo 240 minutos.")
        Integer tempoPreparoMin,

        @NotNull(message = "Escolha o destino do preparo.")
        DestinoPreparo destinoPreparo) {
}
