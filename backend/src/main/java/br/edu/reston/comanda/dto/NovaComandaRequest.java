package br.edu.reston.comanda.dto;

import jakarta.validation.constraints.Size;

public record NovaComandaRequest(
        // Opcional: ajuda a lembrar quem é o "Cliente 2" (sem CPF nem dados pessoais)
        @Size(max = 60, message = "O nome pode ter no máximo 60 caracteres.")
        String nomeCliente) {
}
