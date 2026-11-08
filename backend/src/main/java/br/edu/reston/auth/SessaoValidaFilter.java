package br.edu.reston.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import br.edu.reston.funcionario.Funcionario;
import br.edu.reston.funcionario.FuncionarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

// Confere no banco, a cada requisição, se a pessoa ainda pode entrar.
// Sem isso, quem foi bloqueado continuaria com acesso até a sessão acabar.
// Não é @Component de propósito: já entra na cadeia do Security (SegurancaConfig).
public class SessaoValidaFilter extends OncePerRequestFilter {

    // Com senha provisória, a pessoa só pode fazer estas três coisas
    private static final Set<String> LIBERADOS_COM_SENHA_PROVISORIA =
            Set.of("/api/v1/auth/me", "/api/v1/auth/alterar-senha", "/api/v1/auth/logout");

    private final FuncionarioRepository repositorio;

    public SessaoValidaFilter(FuncionarioRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadeia)
            throws ServletException, IOException {

        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacao != null && autenticacao.getPrincipal() instanceof FuncionarioDetalhes logado) {
            Optional<Funcionario> atual = repositorio.findById(logado.getId());

            if (atual.isEmpty() || !atual.get().isAtivo() || atual.get().isBloqueado()) {
                // Não vale mais: encerra a sessão e segue como se não estivesse logado (resulta em 401)
                SecurityContextHolder.clearContext();
                HttpSession sessao = request.getSession(false);
                if (sessao != null) {
                    sessao.invalidate();
                }
            } else {
                // Troca o usuário da sessão por um com os dados atuais (perfil, senha provisória...)
                FuncionarioDetalhes fresco = new FuncionarioDetalhes(atual.get());
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(fresco, null, fresco.getAuthorities()));

                if (fresco.isSenhaProvisoria() && !LIBERADOS_COM_SENHA_PROVISORIA.contains(request.getRequestURI())) {
                    exigirTrocaDeSenha(response);
                    return;
                }
            }
        }

        cadeia.doFilter(request, response);
    }

    private void exigirTrocaDeSenha(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":403,\"detail\":\"Troque a senha provisória antes de continuar.\",\"codigo\":\"TROCAR_SENHA\"}");
    }
}
