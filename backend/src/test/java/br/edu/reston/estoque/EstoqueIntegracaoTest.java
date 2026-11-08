package br.edu.reston.estoque;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import br.edu.reston.BaseIntegracaoTest;
import br.edu.reston.funcionario.Perfil;

class EstoqueIntegracaoTest extends BaseIntegracaoTest {

    private Integer id(MvcResult resultado, String caminho) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), caminho);
    }

    private record Cenario(MockHttpSession admin, MockHttpSession garcom, MockHttpSession cozinha, Integer comanda, Integer prato,
            Integer refri) {
    }

    private Integer criarProduto(MockHttpSession admin, Integer categoria, String nome, String destino) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/produtos").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoriaId\":" + categoria + ",\"nome\":\"" + nome + "\",\"preco\":20.00,\"tempoPreparoMin\":10,\"destinoPreparo\":\""
                        + destino + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return id(r, "$.id");
    }

    private void controlar(MockHttpSession admin, Integer produto, int inicial, int minimo) throws Exception {
        mvc.perform(put("/api/v1/estoque/" + produto + "/controle").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"controla\":true,\"minimo\":" + minimo + ",\"quantidadeInicial\":" + inicial + "}"))
                .andExpect(status().isOk());
    }

    private Cenario montar() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession garcom = criarEEntrar(admin, "gabi", Perfil.GARCOM);
        MockHttpSession cozinha = criarEEntrar(admin, "cris", Perfil.COZINHA);
        Long mesa = criarMesa(admin, 1, 4);
        Integer categoria = id(mvc.perform(post("/api/v1/categorias").with(csrf()).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content(json("nome", "Tudo"))).andReturn(), "$.id");
        Integer prato = criarProduto(admin, categoria, "Risoto", "COZINHA");
        Integer refri = criarProduto(admin, categoria, "Refrigerante", "SEM_PREPARO");

        Integer atendimento = id(mvc.perform(post("/api/v1/mesas/" + mesa + "/atendimentos").with(csrf()).session(garcom))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        Integer comanda = id(mvc.perform(post("/api/v1/atendimentos/" + atendimento + "/comandas").with(csrf()).session(garcom))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        return new Cenario(admin, garcom, cozinha, comanda, prato, refri);
    }

    private ResultActionsHelper pedir(Cenario c, Integer produto, int quantidade) throws Exception {
        return new ResultActionsHelper(mvc.perform(post("/api/v1/comandas/" + c.comanda + "/pedidos").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itens\":[{\"produtoId\":" + produto + ",\"quantidade\":" + quantidade + "}]}")).andReturn());
    }

    private record ResultActionsHelper(MvcResult resultado) {
        int status() {
            return resultado.getResponse().getStatus();
        }
    }

    private void conferirSaldo(Cenario c, Integer produto, int saldo) throws Exception {
        mvc.perform(get("/api/v1/estoque").session(c.admin))
                .andExpect(jsonPath("$[?(@.produtoId==" + produto + ")].quantidade").value(saldo));
    }

    @Test
    void pedidoBaixaOEstoqueEFicaNoHistorico() throws Exception {
        Cenario c = montar();
        controlar(c.admin, c.prato, 10, 3);

        org.junit.jupiter.api.Assertions.assertEquals(201, pedir(c, c.prato, 4).status());

        conferirSaldo(c, c.prato, 6);
        mvc.perform(get("/api/v1/estoque/" + c.prato + "/movimentacoes").session(c.admin))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tipo").value("SAIDA_PEDIDO"))
                .andExpect(jsonPath("$[0].variacao").value(-4))
                .andExpect(jsonPath("$[0].saldoApos").value(6))
                .andExpect(jsonPath("$[0].funcionario").value("Pessoa gabi"));
    }

    @Test
    void pedidoMaiorQueOSaldoEhRecusadoESemBaixaParcial() throws Exception {
        Cenario c = montar();
        controlar(c.admin, c.prato, 3, 1);

        MvcResult r = mvc.perform(post("/api/v1/comandas/" + c.comanda + "/pedidos").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itens\":[{\"produtoId\":" + c.prato + ",\"quantidade\":2},{\"produtoId\":" + c.prato + ",\"quantidade\":2}]}"))
                .andReturn();

        org.junit.jupiter.api.Assertions.assertEquals(422, r.getResponse().getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(r.getResponse().getContentAsString().contains("restam só 1"));
    }

    @Test
    void produtoSemControleNaoTemLimite() throws Exception {
        Cenario c = montar();
        org.junit.jupiter.api.Assertions.assertEquals(201, pedir(c, c.prato, 40).status());
        mvc.perform(get("/api/v1/estoque").session(c.admin)).andExpect(jsonPath("$[?(@.produtoId==" + c.prato + ")].situacao").value(org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    void cancelarItemRecebidoDevolveMasEmPreparoNao() throws Exception {
        Cenario c = montar();
        controlar(c.admin, c.prato, 10, 2);

        MvcResult primeiro = mvc.perform(post("/api/v1/comandas/" + c.comanda + "/pedidos").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content("{\"itens\":[{\"produtoId\":" + c.prato + ",\"quantidade\":3}]}"))
                .andReturn();
        Integer item = id(primeiro, "$[0].itens[0].id");
        conferirSaldo(c, c.prato, 7);

        mvc.perform(post("/api/v1/itens/" + item + "/cancelar").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content(json("motivo", "Cliente desistiu")))
                .andExpect(status().isOk());
        conferirSaldo(c, c.prato, 10);

        MvcResult segundo = mvc.perform(post("/api/v1/comandas/" + c.comanda + "/pedidos").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content("{\"itens\":[{\"produtoId\":" + c.prato + ",\"quantidade\":2}]}"))
                .andReturn();
        Integer outroItem = id(segundo, "$[0].itens[0].id");
        mvc.perform(patch("/api/v1/pedidos/" + id(segundo, "$[0].id") + "/status").with(csrf()).session(c.cozinha)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EM_PREPARO\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/itens/" + outroItem + "/cancelar").with(csrf()).session(c.admin)
                .contentType(MediaType.APPLICATION_JSON).content(json("motivo", "Erro no pedido")))
                .andExpect(status().isOk());
        conferirSaldo(c, c.prato, 8);
    }

    @Test
    void itemSemPreparoCanceladoVoltaMesmoDepoisDePronto() throws Exception {
        Cenario c = montar();
        controlar(c.admin, c.refri, 5, 1);

        MvcResult r = mvc.perform(post("/api/v1/comandas/" + c.comanda + "/pedidos").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content("{\"itens\":[{\"produtoId\":" + c.refri + ",\"quantidade\":2}]}"))
                .andReturn();
        conferirSaldo(c, c.refri, 3);

        mvc.perform(post("/api/v1/itens/" + id(r, "$[0].itens[0].id") + "/cancelar").with(csrf()).session(c.admin)
                .contentType(MediaType.APPLICATION_JSON).content(json("motivo", "Trocado")))
                .andExpect(status().isOk());
        conferirSaldo(c, c.refri, 5);
    }

    @Test
    void entradaEAjusteMudamOSaldoEAsSituacoes() throws Exception {
        Cenario c = montar();
        controlar(c.admin, c.prato, 4, 5);

        mvc.perform(get("/api/v1/estoque").session(c.admin))
                .andExpect(jsonPath("$[?(@.produtoId==" + c.prato + ")].situacao").value("BAIXO"));

        mvc.perform(post("/api/v1/estoque/" + c.prato + "/entradas").with(csrf()).session(c.admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":20,\"observacao\":\"Nota 123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(24))
                .andExpect(jsonPath("$.situacao").value("OK"));

        mvc.perform(post("/api/v1/estoque/" + c.prato + "/ajustes").with(csrf()).session(c.admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":0,\"motivo\":\"Contagem\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(0))
                .andExpect(jsonPath("$.situacao").value("ZERADO"));

        mvc.perform(get("/api/v1/estoque/" + c.prato + "/movimentacoes").session(c.admin))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].tipo").value("AJUSTE"))
                .andExpect(jsonPath("$[0].variacao").value(-24))
                .andExpect(jsonPath("$[1].tipo").value("ENTRADA"));
    }

    @Test
    void ajusteExigeMotivoESaldoDiferente() throws Exception {
        Cenario c = montar();
        controlar(c.admin, c.prato, 4, 1);

        mvc.perform(post("/api/v1/estoque/" + c.prato + "/ajustes").with(csrf()).session(c.admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":2}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/estoque/" + c.prato + "/ajustes").with(csrf()).session(c.admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":4,\"motivo\":\"Contagem\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("igual ao saldo")));
    }

    @Test
    void entradaEmProdutoSemControleEhRecusadaESoGestaoMexeNoEstoque() throws Exception {
        Cenario c = montar();

        mvc.perform(post("/api/v1/estoque/" + c.prato + "/entradas").with(csrf()).session(c.admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":5}"))
                .andExpect(status().isUnprocessableContent());

        mvc.perform(get("/api/v1/estoque").session(c.garcom)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/estoque/" + c.prato + "/entradas").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void desligarOControleLiberaOPedidoSemLimite() throws Exception {
        Cenario c = montar();
        controlar(c.admin, c.prato, 1, 0);
        org.junit.jupiter.api.Assertions.assertEquals(422, pedir(c, c.prato, 2).status());

        mvc.perform(put("/api/v1/estoque/" + c.prato + "/controle").with(csrf()).session(c.admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"controla\":false,\"minimo\":0}"))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(201, pedir(c, c.prato, 2).status());
    }
}
