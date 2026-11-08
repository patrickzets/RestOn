package br.edu.reston.funcionario;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.jayway.jsonpath.JsonPath;

import br.edu.reston.BaseIntegracaoTest;

class FuncionarioIntegracaoTest extends BaseIntegracaoTest {

    @Test
    void garcomNaoAcessaAGestaoDeFuncionarios() throws Exception {
        MockHttpSession garcom = criarEEntrar(entrarComoAdmin(), "gabi", Perfil.GARCOM);

        mvc.perform(get("/api/v1/funcionarios").session(garcom)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auditoria").session(garcom)).andExpect(status().isForbidden());
    }

    @Test
    void gerenteCriaGarcomEORetornoTrazASenhaProvisoriaUmaVez() throws Exception {
        MockHttpSession gerente = criarEEntrar(entrarComoAdmin(), "gil", Perfil.GERENTE);

        mvc.perform(post("/api/v1/funcionarios").with(csrf()).session(gerente).contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "Helena Souza", "login", "Helena", "perfil", "GARCOM")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.funcionario.login").value("helena")) // o login vai em minúsculas
                .andExpect(jsonPath("$.funcionario.perfil").value("GARCOM"))
                .andExpect(jsonPath("$.funcionario.senhaProvisoria").value(true))
                .andExpect(jsonPath("$.senhaProvisoria").value(not(emptyString())));

        // Na listagem a senha não aparece de jeito nenhum
        String lista = mvc.perform(get("/api/v1/funcionarios").session(gerente)).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(lista.contains("senhaHash"));
        org.junit.jupiter.api.Assertions.assertFalse(lista.contains("argon2"));
    }

    @Test
    void gerenteNaoCriaNemMexeEmAdministrador() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession gerente = criarEEntrar(admin, "gil", Perfil.GERENTE);

        mvc.perform(post("/api/v1/funcionarios").with(csrf()).session(gerente).contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "Outro Admin", "login", "admin2", "perfil", "ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("Administrador")));

        Long idDoAdmin = idDoFuncionario(admin, "admin");
        mvc.perform(patch("/api/v1/funcionarios/" + idDoAdmin + "/bloquear").with(csrf()).session(gerente))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginRepetidoNaoEPermitidoMesmoComOutraCaixa() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        criarFuncionario(admin, "dora", Perfil.CAIXA);

        mvc.perform(post("/api/v1/funcionarios").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "Outra Dora", "login", "DORA", "perfil", "GARCOM")))
                .andExpect(status().isConflict());
    }

    @Test
    void camposInvalidosVoltamComAMensagemDeCadaCampo() throws Exception {
        MockHttpSession admin = entrarComoAdmin();

        mvc.perform(post("/api/v1/funcionarios").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "A", "login", "x y", "perfil", "GARCOM")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists())
                .andExpect(jsonPath("$.campos.login").exists());
    }

    @Test
    void ninguemDesativaOuMudaOProprioPerfil() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long meuId = idDoFuncionario(admin, "admin");

        mvc.perform(patch("/api/v1/funcionarios/" + meuId + "/desativar").with(csrf()).session(admin))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(put("/api/v1/funcionarios/" + meuId).with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "Administrador", "perfil", "GERENTE")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Você não pode mudar o seu próprio perfil."));
    }

    @Test
    void quemEhDesativadoNaoEntraMais() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession eva = criarEEntrar(admin, "eva", Perfil.COZINHA);
        Long idDaEva = idDoFuncionario(admin, "eva");

        mvc.perform(patch("/api/v1/funcionarios/" + idDaEva + "/desativar").with(csrf()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mvc.perform(get("/api/v1/auth/me").session(eva)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("login", "eva", "senha", SENHA_NOVA)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trocarOPerfilValeNaHoraParaQuemJaEstaLogado() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession fabio = criarEEntrar(admin, "fabio", Perfil.GERENTE);
        mvc.perform(get("/api/v1/funcionarios").session(fabio)).andExpect(status().isOk());

        Long idDoFabio = idDoFuncionario(admin, "fabio");
        mvc.perform(put("/api/v1/funcionarios/" + idDoFabio).with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "Fabio Lima", "perfil", "GARCOM")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/funcionarios").session(fabio)).andExpect(status().isForbidden());
    }

    @Test
    void redefinirASenhaGeraUmaNovaProvisoriaEExigeATroca() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        criarEEntrar(admin, "gui", Perfil.CAIXA);
        Long idDoGui = idDoFuncionario(admin, "gui");

        String resposta = mvc.perform(post("/api/v1/funcionarios/" + idDoGui + "/redefinir-senha").with(csrf()).session(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String novaProvisoria = JsonPath.read(resposta, "$.senhaProvisoria");

        mvc.perform(get("/api/v1/auth/me").session(entrar("gui", novaProvisoria)))
                .andExpect(jsonPath("$.senhaProvisoria").value(true));
    }

}
