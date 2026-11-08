package br.edu.reston.cardapio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaRequest(
        @NotBlank(message = "Informe o nome da categoria.")
        @Size(min = 2, max = 60, message = "O nome deve ter de 2 a 60 caracteres.")
        String nome) {
}
