package br.edu.reston.funcionario.dto;

// a senha provisória só vai aqui; no banco fica apenas o hash
public record FuncionarioCriadoResposta(FuncionarioResposta funcionario, String senhaProvisoria) {
}
