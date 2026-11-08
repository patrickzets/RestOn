package br.edu.reston.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlterarSenhaRequest(
        @NotBlank(message = "Informe a senha atual.")
        @Size(max = 200, message = "Senha muito longa.")
        String senhaAtual,

        @NotBlank(message = "Informe a nova senha.")
        @Size(max = 200, message = "Senha muito longa.")
        String novaSenha) {
}
