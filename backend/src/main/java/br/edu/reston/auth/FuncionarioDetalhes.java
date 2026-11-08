package br.edu.reston.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import br.edu.reston.funcionario.Funcionario;
import br.edu.reston.funcionario.Perfil;

// o hash da senha é apagado logo depois do login
public class FuncionarioDetalhes implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String nome;
    private final String login;
    private final Perfil perfil;
    private final boolean ativo;
    private final boolean bloqueado;
    private final boolean senhaProvisoria;
    private String senhaHash;

    public FuncionarioDetalhes(Funcionario f) {
        this.id = f.getId();
        this.nome = f.getNome();
        this.login = f.getLogin();
        this.perfil = f.getPerfil();
        this.ativo = f.isAtivo();
        this.bloqueado = f.isBloqueado();
        this.senhaProvisoria = f.isSenhaProvisoria();
        this.senhaHash = f.getSenhaHash();
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public Perfil getPerfil() { return perfil; }
    public boolean isSenhaProvisoria() { return senhaProvisoria; }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }

    @Override
    public String getPassword() { return senhaHash; }

    @Override
    public String getUsername() { return login; }

    @Override
    public boolean isAccountNonLocked() { return !bloqueado; }

    @Override
    public boolean isEnabled() { return ativo; }

    @Override
    public void eraseCredentials() {
        senhaHash = null;
    }
}
