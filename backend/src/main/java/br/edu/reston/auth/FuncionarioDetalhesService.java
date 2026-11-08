package br.edu.reston.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import br.edu.reston.funcionario.FuncionarioRepository;

@Service
public class FuncionarioDetalhesService implements UserDetailsService {

    private final FuncionarioRepository repositorio;

    public FuncionarioDetalhesService(FuncionarioRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        return repositorio.findByLoginIgnoreCase(login)
                .map(FuncionarioDetalhes::new)
                .orElseThrow(() -> new UsernameNotFoundException("Login não encontrado"));
    }
}
