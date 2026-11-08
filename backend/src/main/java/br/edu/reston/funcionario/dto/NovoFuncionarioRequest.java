package br.edu.reston.funcionario.dto;

import br.edu.reston.funcionario.Perfil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NovoFuncionarioRequest(
        @NotBlank(message = "Informe o nome.")
        @Size(min = 2, max = 120, message = "O nome deve ter de 2 a 120 caracteres.")
        String nome,

        @NotBlank(message = "Informe o login.")
        @Size(min = 3, max = 40, message = "O login deve ter de 3 a 40 caracteres.")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Use só letras, números, ponto, hífen e sublinhado.")
        String login,

        @NotNull(message = "Escolha o perfil.")
        Perfil perfil) {
}
