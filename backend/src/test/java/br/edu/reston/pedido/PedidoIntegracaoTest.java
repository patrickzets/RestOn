package br.edu.reston.pedido;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import br.edu.reston.BaseIntegracaoTest;
import br.edu.reston.funcionario.Perfil;

class PedidoIntegracaoTest extends BaseIntegracaoTest {

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager gerenciador;

    private Integer id(MvcResult resultado, String caminho) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), caminho);
    }

    private Integer criarProduto(MockHttpSession admin, Integer categoria, String nome, String preco, String destino) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/produtos").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoriaId\":" + categoria + ",\"nome\":\"" + nome + "\",\"preco\":" + preco
                        + ",\"tempoPreparoMin\":10,\"destinoPreparo\":\"" + destino + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return id(r, "$.id");
    }

    private Integer abrirAtendimento(MockHttpSession sessao, Long mesa) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/mesas/" + mesa + "/atendimentos").with(csrf()).session(sessao))
                .andExpect(status().isCreated()).andReturn();
        return id(r, "$.id");
    }

    private Integer abrirComanda(MockHttpSession sessao, Integer atendimento) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/atendimentos/" + atendimento + "/comandas").with(csrf()).session(sessao))
                .andExpect(status().isCreated()).andReturn();
        return id(r, "$.id");
    }

    private String itens(Object... produtoEQuantidade) {
        StringBuilder texto = new StringBuilder("{\"itens\":[");
        for (int i = 0; i < produtoEQuantidade.length; i += 2) {
            if (i > 0) texto.append(",");
            texto.append("{\"produtoId\":").append(produtoEQuantidade[i]).append(",\"quantidade\":").append(produtoEQuantidade[i + 1]).append("}");
        }
        return texto.append("]}").toString();
    }

    private MvcResult lancar(MockHttpSession sessao, Integer comanda, Object... produtoEQuantidade) throws Exception {
        return mvc.perform(post("/api/v1/comandas/" + comanda + "/pedidos").with(csrf()).session(sessao)
                .contentType(MediaType.APPLICATION_JSON).content(itens(produtoEQuantidade)))
                .andExpect(status().isCreated()).andReturn();
    }

    private void mudarStatus(MockHttpSession sessao, Integer pedido, String status, int esperado) throws Exception {
        mvc.perform(patch("/api/v1/pedidos/" + pedido + "/status").with(csrf()).session(sessao)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().is(esperado));
    }

    // Mesa 1 com atendimento e uma comanda, mais um produto de cada destino
    private record Cenario(MockHttpSession admin, MockHttpSession garcom, MockHttpSession cozinha, Long mesa, Integer atendimento,
            Integer comanda, Integer prato, Integer suco, Integer refri) {
    }

    private Cenario montar() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession garcom = criarEEntrar(admin, "gabi", Perfil.GARCOM);
        MockHttpSession cozinha = criarEEntrar(admin, "cris", Perfil.COZINHA);
        Long mesa = criarMesa(admin, 1, 4);

        MvcResult c = mvc.perform(post("/api/v1/categorias").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "Tudo"))).andReturn();
        Integer categoria = id(c, "$.id");
        Integer prato = criarProduto(admin, categoria, "Risoto", "49.90", "COZINHA");
        Integer suco = criarProduto(admin, categoria, "Suco", "8.00", "BAR");
        Integer refri = criarProduto(admin, categoria, "Refrigerante", "7.00", "SEM_PREPARO");

        Integer atendimento = abrirAtendimento(garcom, mesa);
        Integer comanda = abrirComanda(garcom, atendimento);
        return new Cenario(admin, garcom, cozinha, mesa, atendimento, comanda, prato, suco, refri);
    }

    @Test
    void clientesDoAtendimentoSaoNumeradosEOsCodigosNuncaSeRepetem() throws Exception {
        Cenario c = montar();
        abrirComanda(c.garcom, c.atendimento);
        abrirComanda(c.garcom, c.atendimento);

        mvc.perform(get("/api/v1/comandas?atendimentoId=" + c.atendimento).session(c.garcom))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].numeroCliente").value(1))
                .andExpect(jsonPath("$[1].numeroCliente").value(2))
                .andExpect(jsonPath("$[2].numeroCliente").value(3));

        String codigos = mvc.perform(get("/api/v1/comandas?atendimentoId=" + c.atendimento).session(c.garcom))
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> lista = JsonPath.read(codigos, "$[*].codigo");
        org.junit.jupiter.api.Assertions.assertEquals(3, new java.util.HashSet<>(lista).size());
    }

    @Test
    void umEnvioComDestinosDiferentesViraUmPedidoPorDestino() throws Exception {
        Cenario c = montar();

        MvcResult r = lancar(c.garcom, c.comanda, c.prato, 2, c.suco, 1, c.refri, 3);

        org.junit.jupiter.api.Assertions.assertEquals(3, ((java.util.List<?>) JsonPath.read(r.getResponse().getContentAsString(), "$")).size());
        mvc.perform(get("/api/v1/cozinha/pedidos").session(c.cozinha)).andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/api/v1/cozinha/pedidos?destino=COZINHA").session(c.cozinha))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("RECEBIDO"))
                .andExpect(jsonPath("$[0].mesaNumero").value(1));
        mvc.perform(get("/api/v1/cozinha/pedidos?destino=BAR").session(c.cozinha)).andExpect(jsonPath("$", hasSize(1)));
        // Produto sem preparo já nasce pronto
        mvc.perform(get("/api/v1/cozinha/pedidos?destino=SEM_PREPARO").session(c.cozinha))
                .andExpect(jsonPath("$[0].status").value("PRONTO"));
    }

    @Test
    void totalDaComandaUsaBigDecimalComTaxaDeServico() throws Exception {
        Cenario c = montar();
        lancar(c.garcom, c.comanda, c.prato, 2, c.suco, 1); // 2 x 49,90 + 8,00 = 107,80

        mvc.perform(get("/api/v1/comandas/" + c.comanda).session(c.garcom))
                .andExpect(jsonPath("$.subtotal").value(107.80))
                .andExpect(jsonPath("$.taxaPercentual").value(10.00))
                .andExpect(jsonPath("$.taxaValor").value(10.78))
                .andExpect(jsonPath("$.total").value(118.58));
    }

    @Test
    void mudarATaxaDepoisNaoAlteraUmaComandaJaAberta() throws Exception {
        Cenario c = montar();
        lancar(c.garcom, c.comanda, c.prato, 1);

        jdbc.update("update configuracao set valor = '20.00' where chave = 'taxa_servico_percentual'");
        gerenciador.clear(); // o teste roda numa transação só, então esquece o que o Hibernate já tinha lido

        mvc.perform(get("/api/v1/comandas/" + c.comanda).session(c.garcom))
                .andExpect(jsonPath("$.taxaPercentual").value(10.00))
                .andExpect(jsonPath("$.taxaValor").value(4.99));

        // Uma comanda nova já usa a taxa nova
        Integer outra = abrirComanda(c.garcom, c.atendimento);
        mvc.perform(get("/api/v1/comandas/" + outra).session(c.garcom)).andExpect(jsonPath("$.taxaPercentual").value(20.00));
    }

    @Test
    void produtoIndisponivelOuArquivadoNaoEntraNoPedido() throws Exception {
        Cenario c = montar();
        mvc.perform(patch("/api/v1/produtos/" + c.prato + "/disponibilidade").with(csrf()).session(c.admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"disponivel\":false}")).andExpect(status().isOk());

        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/pedidos").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content(itens(c.prato, 1, c.suco, 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("não está disponível")));

        // Nada foi criado, nem o pedido do suco
        mvc.perform(get("/api/v1/cozinha/pedidos").session(c.cozinha)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void pedidoVazioOuComQuantidadeInvalidaEhRecusado() throws Exception {
        Cenario c = montar();

        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/pedidos").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content("{\"itens\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/pedidos").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content(itens(c.prato, 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pedidoSegueOFluxoRecebidoPreparoProntoEntregueUmPassoDeCadaVez() throws Exception {
        Cenario c = montar();
        MvcResult r = lancar(c.garcom, c.comanda, c.prato, 1);
        Integer pedido = id(r, "$[0].id");

        mudarStatus(c.cozinha, pedido, "ENTREGUE", 422);     // não pula etapa
        mudarStatus(c.cozinha, pedido, "EM_PREPARO", 200);
        mudarStatus(c.cozinha, pedido, "EM_PREPARO", 422);   // não repete
        mudarStatus(c.cozinha, pedido, "PRONTO", 200);
        mudarStatus(c.garcom, pedido, "ENTREGUE", 200);
        mudarStatus(c.garcom, pedido, "PRONTO", 422);        // não volta atrás

        // Pedido entregue sai do painel da cozinha
        mvc.perform(get("/api/v1/cozinha/pedidos").session(c.cozinha)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void cozinhaNaoEntregaEGarcomNaoPrepara() throws Exception {
        Cenario c = montar();
        MvcResult r = lancar(c.garcom, c.comanda, c.prato, 1);
        Integer pedido = id(r, "$[0].id");

        mudarStatus(c.garcom, pedido, "EM_PREPARO", 403);
        mudarStatus(c.cozinha, pedido, "EM_PREPARO", 200);
        mudarStatus(c.cozinha, pedido, "PRONTO", 200);
        mudarStatus(c.cozinha, pedido, "ENTREGUE", 403);
    }

    @Test
    void mudarOStatusDeUmPedidoNaoAlteraOsOutros() throws Exception {
        Cenario c = montar();
        MvcResult r = lancar(c.garcom, c.comanda, c.prato, 1, c.suco, 1);
        Integer pedidoDoPrato = id(r, "$[0].id");

        mudarStatus(c.cozinha, pedidoDoPrato, "EM_PREPARO", 200);

        mvc.perform(get("/api/v1/cozinha/pedidos?destino=COZINHA").session(c.cozinha)).andExpect(jsonPath("$[0].status").value("EM_PREPARO"));
        mvc.perform(get("/api/v1/cozinha/pedidos?destino=BAR").session(c.cozinha)).andExpect(jsonPath("$[0].status").value("RECEBIDO"));
    }

    @Test
    void garcomCancelaItemRecebidoMasNaoOQueJaEstaEmPreparo() throws Exception {
        Cenario c = montar();
        MvcResult r = lancar(c.garcom, c.comanda, c.prato, 1, c.suco, 1);
        Integer itemDoPrato = id(r, "$[0].itens[0].id");
        Integer itemDoSuco = id(r, "$[1].itens[0].id");
        Integer pedidoDoPrato = id(r, "$[0].id");

        // Ainda recebido: o garçom cancela
        mvc.perform(post("/api/v1/itens/" + itemDoSuco + "/cancelar").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content(json("motivo", "Cliente desistiu")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        // Em preparo: o garçom não cancela, o gerente sim
        mudarStatus(c.cozinha, pedidoDoPrato, "EM_PREPARO", 200);
        mvc.perform(post("/api/v1/itens/" + itemDoPrato + "/cancelar").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content(json("motivo", "Erro no pedido")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/itens/" + itemDoPrato + "/cancelar").with(csrf()).session(c.admin)
                .contentType(MediaType.APPLICATION_JSON).content(json("motivo", "Erro no pedido")))
                .andExpect(status().isOk());

        // O total só conta o que não foi cancelado
        mvc.perform(get("/api/v1/comandas/" + c.comanda).session(c.garcom)).andExpect(jsonPath("$.subtotal").value(0));
    }

    @Test
    void cancelarItemExigeMotivoENaoVaiParaItemEntregue() throws Exception {
        Cenario c = montar();
        MvcResult r = lancar(c.garcom, c.comanda, c.refri, 1);
        Integer pedido = id(r, "$[0].id");
        Integer item = id(r, "$[0].itens[0].id");

        mvc.perform(post("/api/v1/itens/" + item + "/cancelar").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mudarStatus(c.garcom, pedido, "ENTREGUE", 200);
        mvc.perform(post("/api/v1/itens/" + item + "/cancelar").with(csrf()).session(c.admin)
                .contentType(MediaType.APPLICATION_JSON).content(json("motivo", "Tarde demais")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("já foi entregue")));
    }

    @Test
    void itemSeTransfereParaOutraComandaDaMesmaMesaEOTotalAcompanha() throws Exception {
        Cenario c = montar();
        Integer segunda = abrirComanda(c.garcom, c.atendimento);
        MvcResult r = lancar(c.garcom, c.comanda, c.prato, 1, c.suco, 1);
        Integer itemDoPrato = id(r, "$[0].itens[0].id");

        mvc.perform(post("/api/v1/itens/" + itemDoPrato + "/transferir").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content("{\"comandaDestinoId\":" + segunda + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comandaId").value(segunda));

        mvc.perform(get("/api/v1/comandas/" + c.comanda).session(c.garcom)).andExpect(jsonPath("$.subtotal").value(8.00));
        mvc.perform(get("/api/v1/comandas/" + segunda).session(c.garcom)).andExpect(jsonPath("$.subtotal").value(49.90));
    }

    @Test
    void naoTransfereItemParaComandaDeOutraMesa() throws Exception {
        Cenario c = montar();
        Long outraMesa = criarMesa(c.admin, 2, 4);
        Integer outroAtendimento = abrirAtendimento(c.garcom, outraMesa);
        Integer comandaDeFora = abrirComanda(c.garcom, outroAtendimento);
        MvcResult r = lancar(c.garcom, c.comanda, c.prato, 1);
        Integer item = id(r, "$[0].itens[0].id");

        mvc.perform(post("/api/v1/itens/" + item + "/transferir").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content("{\"comandaDestinoId\":" + comandaDeFora + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("mesma mesa")));
    }

    @Test
    void soCancelaComandaQueEstaVazia() throws Exception {
        Cenario c = montar();
        Integer vazia = abrirComanda(c.garcom, c.atendimento);
        lancar(c.garcom, c.comanda, c.prato, 1);

        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/cancelar").with(csrf()).session(c.garcom))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("tem itens")));
        mvc.perform(post("/api/v1/comandas/" + vazia + "/cancelar").with(csrf()).session(c.garcom))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    @Test
    void mesaSoVaiParaAguardandoLiberacaoSemComandasAbertas() throws Exception {
        Cenario c = montar();

        mvc.perform(post("/api/v1/atendimentos/" + c.atendimento + "/encerrar").with(csrf()).session(c.garcom))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("comandas abertas")));

        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/cancelar").with(csrf()).session(c.garcom)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/atendimentos/" + c.atendimento + "/encerrar").with(csrf()).session(c.garcom))
                .andExpect(status().isOk());
    }

    @Test
    void painelDeMesasMostraComandasAbertasEPedidosProntos() throws Exception {
        Cenario c = montar();
        lancar(c.garcom, c.comanda, c.refri, 1); // sem preparo: já nasce pronto

        mvc.perform(get("/api/v1/mesas").session(c.garcom))
                .andExpect(jsonPath("$[0].comandasAbertas").value(1))
                .andExpect(jsonPath("$[0].pedidosProntos").value(1));
    }

    @Test
    void caixaEleiturasDeComandaMasNaoLancamPedido() throws Exception {
        Cenario c = montar();
        MockHttpSession caixa = criarEEntrar(c.admin, "caio", Perfil.CAIXA);

        mvc.perform(get("/api/v1/comandas/" + c.comanda).session(caixa)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/pedidos").with(csrf()).session(caixa)
                .contentType(MediaType.APPLICATION_JSON).content(itens(c.prato, 1)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cozinha/pedidos").session(caixa)).andExpect(status().isForbidden());
    }

    @Test
    void cozinhaNaoAbreComandaNemVeMesas() throws Exception {
        Cenario c = montar();

        mvc.perform(post("/api/v1/atendimentos/" + c.atendimento + "/comandas").with(csrf()).session(c.cozinha))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mesas").session(c.cozinha)).andExpect(status().isForbidden());
    }

    @Test
    void tempoRealSoParaQuemPodeVerAsTelas() throws Exception {
        Cenario c = montar();
        MockHttpSession caixa = criarEEntrar(c.admin, "caio", Perfil.CAIXA);

        mvc.perform(get("/api/v1/stream/pedidos").session(caixa)).andExpect(request().asyncStarted());
        mvc.perform(get("/api/v1/stream/mesas").session(c.cozinha)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/stream/pedidos").session(c.cozinha)).andExpect(request().asyncStarted());
        mvc.perform(get("/api/v1/stream/mesas").session(c.garcom)).andExpect(request().asyncStarted());
    }
}
