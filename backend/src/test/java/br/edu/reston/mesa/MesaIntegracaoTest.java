package br.edu.reston.mesa;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import br.edu.reston.BaseIntegracaoTest;
import br.edu.reston.funcionario.Perfil;

class MesaIntegracaoTest extends BaseIntegracaoTest {

    @Test
    void garcomVeAsMesasMasNaoCadastra() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession garcom = criarEEntrar(admin, "gabi", Perfil.GARCOM);
        criarMesa(admin, 1, 4);

        mvc.perform(get("/api/v1/mesas").session(garcom))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[0].estado").value("LIVRE"));

        mvc.perform(post("/api/v1/mesas").with(csrf()).session(garcom).contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":2,\"capacidade\":4,\"ativa\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mesaGuardaOSetorEVaiParaOSalaoQuandoNaoInformado() throws Exception {
        MockHttpSession admin = entrarComoAdmin();

        mvc.perform(post("/api/v1/mesas").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":10,\"capacidade\":4,\"ativa\":true,\"setor\":\"Varanda\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.setor").value("Varanda"));
        mvc.perform(post("/api/v1/mesas").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":11,\"capacidade\":4,\"ativa\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.setor").value("Salão"));
    }

    @Test
    void caixaECozinhaNaoAcessamAsMesas() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession caixa = criarEEntrar(admin, "caio", Perfil.CAIXA);
        MockHttpSession cozinha = criarEEntrar(admin, "cris", Perfil.COZINHA);

        mvc.perform(get("/api/v1/mesas").session(caixa)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mesas").session(cozinha)).andExpect(status().isForbidden());
    }

    @Test
    void numeroDeMesaRepetidoEhRecusado() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        criarMesa(admin, 7, 4);

        mvc.perform(post("/api/v1/mesas").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":7,\"capacidade\":2,\"ativa\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("mesa 7")));
    }

    @Test
    void cicloCompletoDaMesaLivreOcupadaAguardandoLivre() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession garcom = criarEEntrar(admin, "gabi", Perfil.GARCOM);
        Long mesa = criarMesa(admin, 1, 4);

        MvcResult aberto = mvc.perform(post("/api/v1/mesas/" + mesa + "/atendimentos").with(csrf()).session(garcom))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mesaNumero").value(1))
                .andReturn();
        Integer atendimento = JsonPath.read(aberto.getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/v1/mesas").session(garcom)).andExpect(jsonPath("$[0].estado").value("OCUPADA"));

        // Com a mesa ocupada não dá para abrir outro atendimento nem liberar
        mvc.perform(post("/api/v1/mesas/" + mesa + "/atendimentos").with(csrf()).session(garcom))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/mesas/" + mesa + "/liberar").with(csrf()).session(garcom))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/v1/atendimentos/" + atendimento + "/encerrar").with(csrf()).session(garcom))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/mesas").session(garcom)).andExpect(jsonPath("$[0].estado").value("AGUARDANDO_LIBERACAO"));

        // Encerrar duas vezes não pode
        mvc.perform(post("/api/v1/atendimentos/" + atendimento + "/encerrar").with(csrf()).session(garcom))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/v1/mesas/" + mesa + "/liberar").with(csrf()).session(garcom)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mesas").session(garcom)).andExpect(jsonPath("$[0].estado").value("LIVRE"));
    }

    @Test
    void trocarDeMesaMudaOsDoisEstados() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long origem = criarMesa(admin, 1, 4);
        Long destino = criarMesa(admin, 2, 4);

        MvcResult aberto = mvc.perform(post("/api/v1/mesas/" + origem + "/atendimentos").with(csrf()).session(admin))
                .andReturn();
        Integer atendimento = JsonPath.read(aberto.getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/atendimentos/" + atendimento + "/trocar-mesa").with(csrf()).session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"novaMesaId\":" + destino + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesaNumero").value(2));

        mvc.perform(get("/api/v1/mesas").session(admin))
                .andExpect(jsonPath("$[0].estado").value("AGUARDANDO_LIBERACAO"))
                .andExpect(jsonPath("$[1].estado").value("OCUPADA"))
                .andExpect(jsonPath("$[1].atendimentoId").value(atendimento));
    }

    @Test
    void naoTrocaParaUmaMesaOcupada() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long a = criarMesa(admin, 1, 4);
        Long b = criarMesa(admin, 2, 4);
        MvcResult aberto = mvc.perform(post("/api/v1/mesas/" + a + "/atendimentos").with(csrf()).session(admin)).andReturn();
        mvc.perform(post("/api/v1/mesas/" + b + "/atendimentos").with(csrf()).session(admin)).andExpect(status().isCreated());
        Integer atendimento = JsonPath.read(aberto.getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/atendimentos/" + atendimento + "/trocar-mesa").with(csrf()).session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"novaMesaId\":" + b + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("não está livre")));
    }

    @Test
    void naoDesativaMesaOcupada() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa = criarMesa(admin, 1, 4);
        mvc.perform(post("/api/v1/mesas/" + mesa + "/atendimentos").with(csrf()).session(admin)).andExpect(status().isCreated());

        mvc.perform(put("/api/v1/mesas/" + mesa).with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":1,\"capacidade\":4,\"ativa\":false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("livre")));
    }

    @Test
    void capacidadeInvalidaEhRecusada() throws Exception {
        MockHttpSession admin = entrarComoAdmin();

        mvc.perform(post("/api/v1/mesas").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":1,\"capacidade\":0,\"ativa\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.capacidade").exists());
    }
}
