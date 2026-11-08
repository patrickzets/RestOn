package br.edu.reston.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import br.edu.reston.auth.FuncionarioDetalhesService;
import br.edu.reston.auth.SessaoValidaFilter;
import br.edu.reston.funcionario.FuncionarioRepository;

// Sessão por cookie HttpOnly, CSRF por cookie XSRF-TOKEN e senha com Argon2id.
// O que cada perfil pode fazer fica nos controllers, com @PreAuthorize.
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SegurancaConfig {

    @Bean
    PasswordEncoder codificadorDeSenha() {
        // saltLength=16, hashLength=32, paralelismo=1, memória=19 MiB (19456 KiB), iterações=2
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        // O prefixo {argon2} no hash permite trocar o algoritmo no futuro sem perder as senhas antigas
        return new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2, "bcrypt", new BCryptPasswordEncoder(12)));
    }

    @Bean
    AuthenticationManager gerenteDeAutenticacao(FuncionarioDetalhesService usuarios, PasswordEncoder codificador) {
        DaoAuthenticationProvider provedor = new DaoAuthenticationProvider(usuarios);
        provedor.setPasswordEncoder(codificador);
        return new ProviderManager(provedor);
    }

    @Bean
    SecurityContextRepository repositorioDeContexto() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, FuncionarioRepository funcionarios, SecurityContextRepository repositorioDeContexto)
            throws Exception {

        // Para aplicações de página única (SPA): o token CSRF é lido do cookie, sem a versão "embaralhada"
        CsrfTokenRequestAttributeHandler tratadorCsrf = new CsrfTokenRequestAttributeHandler();
        tratadorCsrf.setCsrfRequestAttributeName(null);

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(tratadorCsrf))
            .securityContext(contexto -> contexto.securityContextRepository(repositorioDeContexto))
            .sessionManagement(sessao -> sessao.sessionFixation(fixacao -> fixacao.changeSessionId()))
            // Sem login, a API responde 401 (em vez de redirecionar para uma página de login)
            .exceptionHandling(erros -> erros.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(regras -> regras
                .requestMatchers(HttpMethod.GET, "/api/v1/saude").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                // A documentação da API fica aberta só para desenvolvimento: feche antes de publicar
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            // Confere no banco, a cada requisição, se a pessoa logada continua valendo
            .addFilterAfter(new SessaoValidaFilter(funcionarios), SecurityContextHolderFilter.class);

        return http.build();
    }
}
