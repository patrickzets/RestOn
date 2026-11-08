package br.edu.reston.funcionario.dto;

import br.edu.reston.funcionario.Perfil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtualizarFuncionarioRequest(
        @NotBlank(message = "Informe o nome.")
        @Size(min = 2, max = 120, message = "O nome deve ter de 2 a 120 caracteres.")
        String nome,

        @NotNull(message = "Escolha o perfil.")
        Perfil perfil) {
}
