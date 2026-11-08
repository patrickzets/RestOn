package br.edu.reston.funcionario.dto;

import java.time.Instant;

import br.edu.reston.funcionario.Funcionario;
import br.edu.reston.funcionario.Perfil;

public record FuncionarioResposta(
        Long id,
        String nome,
        String login,
        Perfil perfil,
        boolean ativo,
        boolean bloqueado,
        boolean senhaProvisoria,
        Instant ultimoAcessoEm,
        Instant criadoEm) {

    public static FuncionarioResposta de(Funcionario f) {
        return new FuncionarioResposta(f.getId(), f.getNome(), f.getLogin(), f.getPerfil(), f.isAtivo(),
                f.isBloqueado(), f.isSenhaProvisoria(), f.getUltimoAcessoEm(), f.getCriadoEm());
    }
}
