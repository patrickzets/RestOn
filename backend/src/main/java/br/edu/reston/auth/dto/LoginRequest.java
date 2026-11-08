package br.edu.reston.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Informe o login.")
        @Size(max = 60, message = "Login muito longo.")
        String login,

        @NotBlank(message = "Informe a senha.")
        @Size(max = 200, message = "Senha muito longa.")
        String senha) {
}
