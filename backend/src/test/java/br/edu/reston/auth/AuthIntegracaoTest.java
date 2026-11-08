package br.edu.reston.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import br.edu.reston.BaseIntegracaoTest;
import br.edu.reston.funcionario.Perfil;

class AuthIntegracaoTest extends BaseIntegracaoTest {

    @Test
    void loginCorretoDevolveOUsuarioEMantemASessao() throws Exception {
        MockHttpSession sessao = entrarComoAdmin();

        mvc.perform(get("/api/v1/auth/me").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("admin"))
                .andExpect(jsonPath("$.perfil").value("ADMIN"))
                .andExpect(jsonPath("$.senhaProvisoria").value(false));
    }

    @Test
    void semLoginAApiResponde401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/funcionarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginSemTokenCsrfEhRecusado() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("login", "admin", "senha", SENHA_ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void senhaErradaELoginInexistenteDaoAMesmaResposta() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("login", "admin", "senha", "senha-errada-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Login ou senha inválidos."));

        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("login", "ninguem", "senha", "senha-errada-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Login ou senha inválidos."));
    }

    @Test
    void cincoSenhasErradasSeguidasBloqueiamAConta() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession ana = criarEEntrar(admin, "ana", Perfil.GARCOM);

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(json("login", "ana", "senha", "errada-errada-1")))
                    .andExpect(status().isUnauthorized());
        }

        // Agora nem a senha certa entra
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("login", "ana", "senha", SENHA_NOVA)))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/funcionarios").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.login=='ana')].bloqueado").value(true));
    }

    @Test
    void quemEhBloqueadoPerdeOAcessoNaHora() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession bia = criarEEntrar(admin, "bia", Perfil.CAIXA);
        mvc.perform(get("/api/v1/auth/me").session(bia)).andExpect(status().isOk());

        Long idDaBia = idDoFuncionario(admin, "bia");
        mvc.perform(patch("/api/v1/funcionarios/" + idDaBia + "/bloquear").with(csrf()).session(admin))
                .andExpect(status().isOk());

        // A sessão da Bia já estava aberta, mas deixa de valer
        mvc.perform(get("/api/v1/auth/me").session(bia)).andExpect(status().isUnauthorized());
    }

    @Test
    void senhaProvisoriaObrigaATrocarAntesDeUsarOSistema() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        String provisoria = criarFuncionario(admin, "caio", Perfil.GERENTE);
        MockHttpSession caio = entrar("caio", provisoria);

        mvc.perform(get("/api/v1/auth/me").session(caio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senhaProvisoria").value(true));

        mvc.perform(get("/api/v1/funcionarios").session(caio))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("TROCAR_SENHA"));

        mvc.perform(post("/api/v1/auth/alterar-senha").with(csrf()).session(caio).contentType(MediaType.APPLICATION_JSON)
                .content(json("senhaAtual", provisoria, "novaSenha", SENHA_NOVA)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/funcionarios").session(caio)).andExpect(status().isOk());
    }

    @Test
    void novaSenhaFracaEhRecusada() throws Exception {
        MockHttpSession admin = entrarComoAdmin();

        mvc.perform(post("/api/v1/auth/alterar-senha").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json("senhaAtual", SENHA_ADMIN, "novaSenha", "curta1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("pelo menos 10")));

        mvc.perform(post("/api/v1/auth/alterar-senha").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json("senhaAtual", "senha-atual-errada9", "novaSenha", SENHA_NOVA)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A senha atual está errada."));
    }

    @Test
    void logoutEncerraASessao() throws Exception {
        MockHttpSession sessao = entrarComoAdmin();

        mvc.perform(post("/api/v1/auth/logout").with(csrf()).session(sessao)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me").session(sessao)).andExpect(status().isUnauthorized());
    }

    @Test
    void aAuditoriaRegistraLoginEFalha() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("login", "admin", "senha", "errada-errada-1")))
                .andExpect(status().isUnauthorized());
        MockHttpSession admin = entrarComoAdmin();

        mvc.perform(get("/api/v1/auditoria").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("LOGIN_FALHA")))
                .andExpect(content().string(containsString("\"acao\":\"LOGIN\"")))
                .andExpect(content().string(containsString("ADMIN_INICIAL_CRIADO")));
    }

}
