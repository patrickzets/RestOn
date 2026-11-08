package br.edu.reston.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.auth.dto.AlterarSenhaRequest;
import br.edu.reston.auth.dto.LoginRequest;
import br.edu.reston.auth.dto.UsuarioLogado;
import br.edu.reston.erro.CredenciaisInvalidasException;
import br.edu.reston.funcionario.FuncionarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final SecurityContextHolderStrategy contexto = SecurityContextHolder.getContextHolderStrategy();

    private final AuthenticationManager gerenteDeAutenticacao;
    private final SecurityContextRepository repositorioDeContexto;
    private final FuncionarioService funcionarioService;
    private final AuditoriaService auditoria;

    public AuthController(AuthenticationManager gerenteDeAutenticacao, SecurityContextRepository repositorioDeContexto,
            FuncionarioService funcionarioService, AuditoriaService auditoria) {
        this.gerenteDeAutenticacao = gerenteDeAutenticacao;
        this.repositorioDeContexto = repositorioDeContexto;
        this.funcionarioService = funcionarioService;
        this.auditoria = auditoria;
    }

    @PostMapping("/login")
    public UsuarioLogado login(@Valid @RequestBody LoginRequest dados, HttpServletRequest request, HttpServletResponse response) {
        String login = dados.login().trim();

        Authentication autenticacao;
        try {
            autenticacao = gerenteDeAutenticacao.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(login, dados.senha()));
        } catch (BadCredentialsException erro) {
            funcionarioService.registrarFalhaDeLogin(login);
            throw new CredenciaisInvalidasException();
        } catch (AuthenticationException erro) {
            // Conta bloqueada ou desativada: registra, mas responde igual à senha errada
            auditoria.registrar(null, login, "LOGIN_NEGADO", "funcionario", null, erro.getClass().getSimpleName());
            throw new CredenciaisInvalidasException();
        }

        // Troca o id da sessão depois do login (protege contra fixação de sessão)
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext novoContexto = contexto.createEmptyContext();
        novoContexto.setAuthentication(autenticacao);
        contexto.setContext(novoContexto);
        repositorioDeContexto.saveContext(novoContexto, request, response);

        FuncionarioDetalhes usuario = (FuncionarioDetalhes) autenticacao.getPrincipal();
        funcionarioService.registrarAcessoOk(usuario.getId());
        auditoria.registrar(usuario.getId(), usuario.getUsername(), "LOGIN", "funcionario", String.valueOf(usuario.getId()), null);
        return UsuarioLogado.de(usuario);
    }

    @GetMapping("/me")
    public UsuarioLogado quemSouEu(@AuthenticationPrincipal FuncionarioDetalhes usuario) {
        return UsuarioLogado.de(usuario);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal FuncionarioDetalhes usuario, HttpServletRequest request, HttpServletResponse response) {
        auditoria.registrar(usuario.getId(), usuario.getUsername(), "LOGOUT", "funcionario", String.valueOf(usuario.getId()), null);
        new SecurityContextLogoutHandler().logout(request, response, contexto.getContext().getAuthentication());
    }

    @PostMapping("/alterar-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterarSenha(@Valid @RequestBody AlterarSenhaRequest dados, @AuthenticationPrincipal FuncionarioDetalhes usuario) {
        funcionarioService.trocarPropriaSenha(usuario.getId(), dados.senhaAtual(), dados.novaSenha());
    }
}
