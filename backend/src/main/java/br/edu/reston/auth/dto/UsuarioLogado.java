package br.edu.reston.auth.dto;

import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.funcionario.Perfil;

public record UsuarioLogado(Long id, String nome, String login, Perfil perfil, boolean senhaProvisoria) {

    public static UsuarioLogado de(FuncionarioDetalhes u) {
        return new UsuarioLogado(u.getId(), u.getNome(), u.getUsername(), u.getPerfil(), u.isSenhaProvisoria());
    }
}
