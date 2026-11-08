package br.edu.reston.pedido;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import br.edu.reston.BaseIntegracaoTest;
import br.edu.reston.funcionario.Perfil;

class BartenderIntegracaoTest extends BaseIntegracaoTest {

    private Integer id(MvcResult resultado, String caminho) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), caminho);
    }

    private Integer criarProduto(MockHttpSession admin, Integer categoria, String nome, String destino) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/produtos").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoriaId\":" + categoria + ",\"nome\":\"" + nome + "\",\"preco\":10.00"
                        + ",\"tempoPreparoMin\":5,\"destinoPreparo\":\"" + destino + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return id(r, "$.id");
    }

    private void mudarStatus(MockHttpSession sessao, Integer pedido, String status, int esperado) throws Exception {
        mvc.perform(patch("/api/v1/pedidos/" + pedido + "/status").with(csrf()).session(sessao)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().is(esperado));
    }

    private record Cenario(MockHttpSession bartender, Integer pedidoBar, Integer pedidoCozinha) {
    }

    // Um pedido do bar e um da cozinha, os dois em RECEBIDO
    private Cenario montar() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession garcom = criarEEntrar(admin, "gabi", Perfil.GARCOM);
        MockHttpSession bartender = criarEEntrar(admin, "bia", Perfil.BARTENDER);
        Long mesa = criarMesa(admin, 1, 4);

        MvcResult c = mvc.perform(post("/api/v1/categorias").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "Tudo"))).andReturn();
        Integer categoria = id(c, "$.id");
        Integer prato = criarProduto(admin, categoria, "Risoto", "COZINHA");
        Integer drink = criarProduto(admin, categoria, "Caipirinha", "BAR");

        MvcResult a = mvc.perform(post("/api/v1/mesas/" + mesa + "/atendimentos").with(csrf()).session(garcom)).andReturn();
        MvcResult cm = mvc.perform(post("/api/v1/atendimentos/" + id(a, "$.id") + "/comandas").with(csrf()).session(garcom)).andReturn();
        Integer comanda = id(cm, "$.id");

        MvcResult r = mvc.perform(post("/api/v1/comandas/" + comanda + "/pedidos").with(csrf()).session(garcom)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itens\":[{\"produtoId\":" + prato + ",\"quantidade\":1},{\"produtoId\":" + drink + ",\"quantidade\":1}]}"))
                .andExpect(status().isCreated()).andReturn();
        String corpo = r.getResponse().getContentAsString();
        java.util.List<Integer> daCozinha = JsonPath.read(corpo, "$[?(@.destino=='COZINHA')].id");
        java.util.List<Integer> doBar = JsonPath.read(corpo, "$[?(@.destino=='BAR')].id");
        Integer pedidoCozinha = daCozinha.get(0);
        Integer pedidoBar = doBar.get(0);
        return new Cenario(bartender, pedidoBar, pedidoCozinha);
    }

    @Test
    void bartenderVeSoOsPedidosDoBarMesmoPedindoOutroDestino() throws Exception {
        Cenario c = montar();

        mvc.perform(get("/api/v1/cozinha/pedidos").session(c.bartender))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].destino").value("BAR"));
        // Em vez de dar 403, o filtro do bar vale sempre e o parâmetro é ignorado
        mvc.perform(get("/api/v1/cozinha/pedidos?destino=COZINHA").session(c.bartender))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].destino").value("BAR"));
    }

    @Test
    void bartenderPreparaPedidoDoBar() throws Exception {
        Cenario c = montar();

        mudarStatus(c.bartender, c.pedidoBar, "EM_PREPARO", 200);
        mudarStatus(c.bartender, c.pedidoBar, "PRONTO", 200);
    }

    @Test
    void bartenderNaoMexeEmPedidoDaCozinha() throws Exception {
        Cenario c = montar();

        mudarStatus(c.bartender, c.pedidoCozinha, "EM_PREPARO", 403);
    }

    @Test
    void bartenderNaoConfirmaEntrega() throws Exception {
        Cenario c = montar();
        mudarStatus(c.bartender, c.pedidoBar, "EM_PREPARO", 200);
        mudarStatus(c.bartender, c.pedidoBar, "PRONTO", 200);

        mudarStatus(c.bartender, c.pedidoBar, "ENTREGUE", 403);
    }

    @Test
    void bartenderNaoCancelaItem() throws Exception {
        Cenario c = montar();

        mvc.perform(post("/api/v1/itens/1/cancelar").with(csrf()).session(c.bartender)
                .contentType(MediaType.APPLICATION_JSON).content(json("motivo", "Cliente desistiu")))
                .andExpect(status().isForbidden());
    }

    @Test
    void bartenderNaoAcessaOutrosModulos() throws Exception {
        Cenario c = montar();

        mvc.perform(get("/api/v1/caixa/turno-atual").session(c.bartender)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/estoque").session(c.bartender)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/painel").session(c.bartender)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/produtos").session(c.bartender)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/comandas").session(c.bartender)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mesas").session(c.bartender)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/funcionarios").session(c.bartender)).andExpect(status().isForbidden());
    }

    @Test
    void bartenderAbreOStreamDePedidosMasNaoOsDeMesas() throws Exception {
        Cenario c = montar();

        mvc.perform(get("/api/v1/stream/pedidos").session(c.bartender).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/stream/mesas").session(c.bartender).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isForbidden());
    }
}
