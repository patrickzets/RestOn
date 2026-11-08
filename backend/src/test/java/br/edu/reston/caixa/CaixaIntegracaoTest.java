package br.edu.reston.caixa;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import br.edu.reston.BaseIntegracaoTest;
import br.edu.reston.funcionario.Perfil;

class CaixaIntegracaoTest extends BaseIntegracaoTest {

    @Autowired
    private JdbcTemplate jdbc;

    private Integer id(MvcResult resultado, String caminho) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), caminho);
    }

    // Mesa 1 com atendimento e uma comanda; produtos: refrigerante (sem preparo, 7,00) e risoto (cozinha, 49,90)
    private record Cenario(MockHttpSession admin, MockHttpSession gerente, MockHttpSession caixa, MockHttpSession garcom,
            MockHttpSession cozinha, Long mesa, Integer atendimento, Integer comanda, Integer refri, Integer prato) {
    }

    private Cenario montar() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession gerente = criarEEntrar(admin, "gil", Perfil.GERENTE);
        MockHttpSession caixa = criarEEntrar(admin, "caio", Perfil.CAIXA);
        MockHttpSession garcom = criarEEntrar(admin, "gabi", Perfil.GARCOM);
        MockHttpSession cozinha = criarEEntrar(admin, "cris", Perfil.COZINHA);
        Long mesa = criarMesa(admin, 1, 4);

        Integer categoria = id(mvc.perform(post("/api/v1/categorias").with(csrf()).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content(json("nome", "Tudo"))).andReturn(), "$.id");
        Integer refri = criarProduto(admin, categoria, "Refrigerante", "7.00", "SEM_PREPARO");
        Integer prato = criarProduto(admin, categoria, "Risoto", "49.90", "COZINHA");

        Integer atendimento = id(mvc.perform(post("/api/v1/mesas/" + mesa + "/atendimentos").with(csrf()).session(garcom))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        Integer comanda = abrirComanda(garcom, atendimento);
        return new Cenario(admin, gerente, caixa, garcom, cozinha, mesa, atendimento, comanda, refri, prato);
    }

    private Integer criarProduto(MockHttpSession admin, Integer categoria, String nome, String preco, String destino) throws Exception {
        return id(mvc.perform(post("/api/v1/produtos").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoriaId\":" + categoria + ",\"nome\":\"" + nome + "\",\"preco\":" + preco
                        + ",\"tempoPreparoMin\":10,\"destinoPreparo\":\"" + destino + "\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.id");
    }

    private Integer abrirComanda(MockHttpSession sessao, Integer atendimento) throws Exception {
        return id(mvc.perform(post("/api/v1/atendimentos/" + atendimento + "/comandas").with(csrf()).session(sessao))
                .andExpect(status().isCreated()).andReturn(), "$.id");
    }

    // Lança o pedido e, se for sem preparo, já entrega (assim a comanda pode fechar)
    private Integer lancar(MockHttpSession garcom, Integer comanda, Integer produto, int quantidade) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/comandas/" + comanda + "/pedidos").with(csrf()).session(garcom)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itens\":[{\"produtoId\":" + produto + ",\"quantidade\":" + quantidade + "}]}"))
                .andExpect(status().isCreated()).andReturn();
        return id(r, "$[0].id");
    }

    private void entregar(MockHttpSession sessao, Integer pedido) throws Exception {
        mvc.perform(patch("/api/v1/pedidos/" + pedido + "/status").with(csrf()).session(sessao)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ENTREGUE\"}")).andExpect(status().isOk());
    }

    private MvcResult abrirCaixa(MockHttpSession sessao, String valorInicial) throws Exception {
        return mvc.perform(post("/api/v1/caixa/turnos").with(csrf()).session(sessao).contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorInicial\":" + valorInicial + "}")).andExpect(status().isCreated()).andReturn();
    }

    private String pagamento(String metodo, String valor, String recebido) {
        return "{\"metodo\":\"" + metodo + "\",\"valor\":" + valor + (recebido == null ? "" : ",\"valorRecebido\":" + recebido) + "}";
    }

    private MvcResult pagar(MockHttpSession sessao, Integer comanda, String corpo, int esperado) throws Exception {
        return mvc.perform(post("/api/v1/comandas/" + comanda + "/pagamentos").with(csrf()).session(sessao)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().is(esperado)).andReturn();
    }

    @Test
    void soExisteUmCaixaAbertoPorVezEOProprioBancoGaranteIsso() throws Exception {
        Cenario c = montar();
        abrirCaixa(c.caixa, "100.00");

        mvc.perform(post("/api/v1/caixa/turnos").with(csrf()).session(c.gerente).contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorInicial\":50}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Já existe um caixa aberto")));

        Long operador = idDoFuncionario(c.admin, "caio");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("insert into caixa_turno (operador_id, valor_inicial, status) values (?, 0, 'ABERTO')", operador));
    }

    @Test
    void semCaixaAbertoNaoSeRecebePagamento() throws Exception {
        Cenario c = montar();
        lancar(c.garcom, c.comanda, c.refri, 1);

        MvcResult r = pagar(c.caixa, c.comanda, pagamento("PIX", "7.70", null), 422);
        org.junit.jupiter.api.Assertions.assertTrue(r.getResponse().getContentAsString().contains("Abra o caixa"));
    }

    @Test
    void contaPodeSerDivididaEPagamentoInsuficienteNaoFechaAComanda() throws Exception {
        Cenario c = montar();
        Integer pedido = lancar(c.garcom, c.comanda, c.refri, 1); // 7,00 + 10% = 7,70
        entregar(c.garcom, pedido);
        abrirCaixa(c.caixa, "0");

        pagar(c.caixa, c.comanda, pagamento("PIX", "3.00", null), 201);
        mvc.perform(get("/api/v1/comandas/" + c.comanda).session(c.caixa))
                .andExpect(jsonPath("$.pago").value(3.00))
                .andExpect(jsonPath("$.restante").value(4.70));

        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/fechar").with(csrf()).session(c.caixa))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Ainda faltam")));

        pagar(c.caixa, c.comanda, pagamento("CARTAO", "4.70", null), 201);
        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/fechar").with(csrf()).session(c.caixa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADA"))
                .andExpect(jsonPath("$.restante").value(0));
    }

    @Test
    void naoSePagaMaisDoQueFalta() throws Exception {
        Cenario c = montar();
        lancar(c.garcom, c.comanda, c.refri, 1);
        abrirCaixa(c.caixa, "0");

        MvcResult r = pagar(c.caixa, c.comanda, pagamento("PIX", "8.00", null), 422);
        org.junit.jupiter.api.Assertions.assertTrue(r.getResponse().getContentAsString().contains("maior do que falta pagar"));
    }

    @Test
    void trocoSoExisteEmDinheiroEOValorRecebidoNaoPodeSerMenor() throws Exception {
        Cenario c = montar();
        lancar(c.garcom, c.comanda, c.refri, 1); // 7,70
        abrirCaixa(c.caixa, "0");

        pagar(c.caixa, c.comanda, pagamento("DINHEIRO", "5.00", "4.00"), 422);
        pagar(c.caixa, c.comanda, pagamento("PIX", "5.00", "10.00"), 422);

        MvcResult r = pagar(c.caixa, c.comanda, pagamento("DINHEIRO", "5.00", "10.00"), 201);
        org.junit.jupiter.api.Assertions.assertTrue(r.getResponse().getContentAsString().contains("\"troco\":5.00"));
    }

    @Test
    void fecharAComandaGravaOTotalEMandaAMesaParaAguardandoLiberacao() throws Exception {
        Cenario c = montar();
        Integer pedido = lancar(c.garcom, c.comanda, c.refri, 1);
        entregar(c.garcom, pedido);
        abrirCaixa(c.caixa, "0");
        pagar(c.caixa, c.comanda, pagamento("PIX", "7.70", null), 201);

        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/fechar").with(csrf()).session(c.caixa)).andExpect(status().isOk());

        mvc.perform(get("/api/v1/mesas").session(c.garcom)).andExpect(jsonPath("$[0].estado").value("AGUARDANDO_LIBERACAO"));

        // Mudar a taxa depois não altera uma comanda fechada
        jdbc.update("update configuracao set valor = '30.00' where chave = 'taxa_servico_percentual'");
        mvc.perform(get("/api/v1/comandas/" + c.comanda).session(c.caixa))
                .andExpect(jsonPath("$.total").value(7.70))
                .andExpect(jsonPath("$.taxaValor").value(0.70));
    }

    @Test
    void naoFechaEnquantoHouverPedidoNaoEntregue() throws Exception {
        Cenario c = montar();
        lancar(c.garcom, c.comanda, c.prato, 1); // 49,90 + 10% = 54,89, ainda na cozinha
        abrirCaixa(c.caixa, "0");
        pagar(c.caixa, c.comanda, pagamento("PIX", "54.89", null), 201);

        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/fechar").with(csrf()).session(c.caixa))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("não ficaram prontos")));
    }

    @Test
    void pedidoProntoJaContaComoEntregueParaFechar() throws Exception {
        Cenario c = montar();
        Integer pedido = lancar(c.garcom, c.comanda, c.prato, 1); // 49,90 + 10% = 54,89
        // A cozinha marca como pronto direto, sem passar por "em preparo", e ninguém confirma a entrega
        mvc.perform(patch("/api/v1/pedidos/" + pedido + "/status").with(csrf()).session(c.cozinha)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PRONTO\"}")).andExpect(status().isOk());
        abrirCaixa(c.caixa, "0");
        pagar(c.caixa, c.comanda, pagamento("PIX", "54.89", null), 201);

        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/fechar").with(csrf()).session(c.caixa)).andExpect(status().isOk());
    }

    @Test
    void comandaSemNadaACobrarNaoFecha() throws Exception {
        Cenario c = montar();
        abrirCaixa(c.caixa, "0");

        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/fechar").with(csrf()).session(c.caixa))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("nada a cobrar")));
    }

    @Test
    void mesaSoLiberaQuandoAUltimaComandaFecha() throws Exception {
        Cenario c = montar();
        Integer segunda = abrirComanda(c.garcom, c.atendimento);
        entregar(c.garcom, lancar(c.garcom, c.comanda, c.refri, 1));
        entregar(c.garcom, lancar(c.garcom, segunda, c.refri, 1));
        abrirCaixa(c.caixa, "0");

        pagar(c.caixa, c.comanda, pagamento("PIX", "7.70", null), 201);
        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/fechar").with(csrf()).session(c.caixa)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mesas").session(c.garcom)).andExpect(jsonPath("$[0].estado").value("OCUPADA"));

        pagar(c.caixa, segunda, pagamento("PIX", "7.70", null), 201);
        mvc.perform(post("/api/v1/comandas/" + segunda + "/fechar").with(csrf()).session(c.caixa)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mesas").session(c.garcom)).andExpect(jsonPath("$[0].estado").value("AGUARDANDO_LIBERACAO"));
    }

    @Test
    void estornoMantemOPagamentoOriginalECriaRegistroNovo() throws Exception {
        Cenario c = montar();
        lancar(c.garcom, c.comanda, c.refri, 1);
        abrirCaixa(c.caixa, "0");
        Integer pagamentoId = id(pagar(c.caixa, c.comanda, pagamento("PIX", "7.70", null), 201), "$.id");

        // O caixa não estorna; sem motivo também não
        mvc.perform(post("/api/v1/pagamentos/" + pagamentoId + "/estornar").with(csrf()).session(c.caixa)
                .contentType(MediaType.APPLICATION_JSON).content(json("motivo", "Erro de digitação")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/pagamentos/" + pagamentoId + "/estornar").with(csrf()).session(c.gerente)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/pagamentos/" + pagamentoId + "/estornar").with(csrf()).session(c.gerente)
                .contentType(MediaType.APPLICATION_JSON).content(json("motivo", "Erro de digitação")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESTORNADO"));

        // O pagamento continua no histórico, com um estorno ligado a ele, e a conta volta a ter saldo
        Integer pagamentos = jdbc.queryForObject("select count(*) from pagamento where id = ?", Integer.class, pagamentoId);
        Integer estornos = jdbc.queryForObject("select count(*) from estorno where pagamento_id = ? and motivo = 'Erro de digitação'", Integer.class, pagamentoId);
        org.junit.jupiter.api.Assertions.assertEquals(1, pagamentos);
        org.junit.jupiter.api.Assertions.assertEquals(1, estornos);
        mvc.perform(get("/api/v1/comandas/" + c.comanda).session(c.caixa))
                .andExpect(jsonPath("$.pago").value(0))
                .andExpect(jsonPath("$.restante").value(7.70))
                .andExpect(jsonPath("$.pagamentos", hasSize(1)));

        // Estornar duas vezes não pode
        mvc.perform(post("/api/v1/pagamentos/" + pagamentoId + "/estornar").with(csrf()).session(c.gerente)
                .contentType(MediaType.APPLICATION_JSON).content(json("motivo", "De novo")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void soGerenteEAdministradorRemovemATaxaComMotivoEFicaAuditado() throws Exception {
        Cenario c = montar();
        lancar(c.garcom, c.comanda, c.refri, 1);

        mvc.perform(patch("/api/v1/comandas/" + c.comanda + "/taxa").with(csrf()).session(c.caixa)
                .contentType(MediaType.APPLICATION_JSON).content("{\"removida\":true,\"motivo\":\"Cortesia\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/comandas/" + c.comanda + "/taxa").with(csrf()).session(c.gerente)
                .contentType(MediaType.APPLICATION_JSON).content("{\"removida\":true}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(patch("/api/v1/comandas/" + c.comanda + "/taxa").with(csrf()).session(c.gerente)
                .contentType(MediaType.APPLICATION_JSON).content("{\"removida\":true,\"motivo\":\"Cortesia da casa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxaValor").value(0))
                .andExpect(jsonPath("$.total").value(7.00));

        Integer auditados = jdbc.queryForObject(
                "select count(*) from audit_log where acao = 'TAXA_REMOVIDA' and detalhes like '%Cortesia da casa%'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, auditados);
    }

    @Test
    void fechamentoDoCaixaCompararODinheiroContadoComOEsperado() throws Exception {
        Cenario c = montar();
        Integer turno = id(abrirCaixa(c.caixa, "100.00"), "$.id");
        lancar(c.garcom, c.comanda, c.refri, 2); // 14,00 + 10% = 15,40
        pagar(c.caixa, c.comanda, pagamento("PIX", "5.40", null), 201);
        pagar(c.caixa, c.comanda, pagamento("DINHEIRO", "10.00", "20.00"), 201);

        mvc.perform(get("/api/v1/caixa/turno-atual").session(c.caixa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPix").value(5.40))
                .andExpect(jsonPath("$.totalDinheiro").value(10.00))
                .andExpect(jsonPath("$.totalRecebido").value(15.40))
                .andExpect(jsonPath("$.dinheiroEsperado").value(110.00));

        // Faltou R$ 1,00 na gaveta
        mvc.perform(post("/api/v1/caixa/turnos/" + turno + "/fechar").with(csrf()).session(c.caixa)
                .contentType(MediaType.APPLICATION_JSON).content("{\"valorContado\":109.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADO"))
                .andExpect(jsonPath("$.diferenca").value(-1.00));

        mvc.perform(get("/api/v1/caixa/turno-atual").session(c.caixa)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/caixa/turnos/" + turno + "/fechar").with(csrf()).session(c.caixa)
                .contentType(MediaType.APPLICATION_JSON).content("{\"valorContado\":109.00}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void umCaixaNaoFechaOTurnoDeOutroCaixaMasOGerenteFecha() throws Exception {
        Cenario c = montar();
        MockHttpSession outroCaixa = criarEEntrar(c.admin, "carol", Perfil.CAIXA);
        Integer turno = id(abrirCaixa(c.caixa, "0"), "$.id");

        mvc.perform(post("/api/v1/caixa/turnos/" + turno + "/fechar").with(csrf()).session(outroCaixa)
                .contentType(MediaType.APPLICATION_JSON).content("{\"valorContado\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/caixa/turnos/" + turno + "/fechar").with(csrf()).session(c.gerente)
                .contentType(MediaType.APPLICATION_JSON).content("{\"valorContado\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void pagamentoAntecipadoViraCreditoNaPrimeiraComandaDaReserva() throws Exception {
        Cenario c = montar();
        Long mesa2 = criarMesa(c.admin, 2, 4);
        Instant inicio = Instant.now().plus(30, ChronoUnit.MINUTES);
        Integer reserva = id(mvc.perform(post("/api/v1/reservas").with(csrf()).session(c.garcom).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mesaId\":" + mesa2 + ",\"nomeCliente\":\"Marina\",\"telefone\":\"(11) 91234-5678\",\"qtdPessoas\":2,\"inicio\":\"" + inicio + "\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.id");

        // Garçom não recebe; dinheiro não vale; gerente registra por PIX; segundo pagamento não
        String corpo = "{\"metodo\":\"PIX\",\"valor\":3.00}";
        mvc.perform(post("/api/v1/reservas/" + reserva + "/pagamento-antecipado").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reservas/" + reserva + "/pagamento-antecipado").with(csrf()).session(c.gerente)
                .contentType(MediaType.APPLICATION_JSON).content("{\"metodo\":\"DINHEIRO\",\"valor\":3.00}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/reservas/" + reserva + "/pagamento-antecipado").with(csrf()).session(c.gerente)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/reservas/" + reserva + "/pagamento-antecipado").with(csrf()).session(c.gerente)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isUnprocessableEntity());

        String de = inicio.minus(1, ChronoUnit.HOURS).toString();
        String ate = inicio.plus(1, ChronoUnit.HOURS).toString();
        mvc.perform(get("/api/v1/reservas?de=" + de + "&ate=" + ate).session(c.gerente))
                .andExpect(jsonPath("$[0].valorAntecipado").value(3.00));

        // O cliente chega: o valor vira crédito na primeira comanda
        Integer atendimento2 = id(mvc.perform(post("/api/v1/reservas/" + reserva + "/chegada").with(csrf()).session(c.garcom))
                .andExpect(status().isOk()).andReturn(), "$.id");
        Integer comanda2 = abrirComanda(c.garcom, atendimento2);
        lancar(c.garcom, comanda2, c.refri, 1); // 7,70

        mvc.perform(get("/api/v1/comandas/" + comanda2).session(c.garcom))
                .andExpect(jsonPath("$.pago").value(3.00))
                .andExpect(jsonPath("$.restante").value(4.70))
                .andExpect(jsonPath("$.pagamentos[0].origem").value("RESERVA"));
    }

    @Test
    void cancelarAReservaEstornaOQueFoiPagoAntecipado() throws Exception {
        Cenario c = montar();
        Long mesa2 = criarMesa(c.admin, 2, 4);
        Instant inicio = Instant.now().plus(3, ChronoUnit.DAYS);
        Integer reserva = id(mvc.perform(post("/api/v1/reservas").with(csrf()).session(c.garcom).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mesaId\":" + mesa2 + ",\"nomeCliente\":\"Marina\",\"telefone\":\"(11) 91234-5678\",\"qtdPessoas\":2,\"inicio\":\"" + inicio + "\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        mvc.perform(post("/api/v1/reservas/" + reserva + "/pagamento-antecipado").with(csrf()).session(c.gerente)
                .contentType(MediaType.APPLICATION_JSON).content("{\"metodo\":\"CARTAO\",\"valor\":20.00}")).andExpect(status().isCreated());

        mvc.perform(post("/api/v1/reservas/" + reserva + "/cancelar").with(csrf()).session(c.garcom)).andExpect(status().isOk());

        Integer estornados = jdbc.queryForObject(
                "select count(*) from pagamento p join estorno e on e.pagamento_id = p.id where p.reserva_id = ? and p.status = 'ESTORNADO' and e.motivo = 'Reserva cancelada'",
                Integer.class, reserva);
        org.junit.jupiter.api.Assertions.assertEquals(1, estornados);
        mvc.perform(get("/api/v1/reservas?de=" + inicio.minus(1, ChronoUnit.HOURS) + "&ate=" + inicio.plus(1, ChronoUnit.HOURS)).session(c.gerente))
                .andExpect(jsonPath("$[0].valorAntecipado").value(0));
    }

    @Test
    void comprovanteTrazItensPagamentosETroco() throws Exception {
        Cenario c = montar();
        entregar(c.garcom, lancar(c.garcom, c.comanda, c.refri, 1));
        abrirCaixa(c.caixa, "0");
        pagar(c.caixa, c.comanda, pagamento("DINHEIRO", "7.70", "10.00"), 201);
        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/fechar").with(csrf()).session(c.caixa)).andExpect(status().isOk());

        mvc.perform(get("/api/v1/comandas/" + c.comanda + "/comprovante").session(c.caixa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADA"))
                .andExpect(jsonPath("$.itens[0].produto").value("Refrigerante"))
                .andExpect(jsonPath("$.subtotal").value(7.00))
                .andExpect(jsonPath("$.taxaValor").value(0.70))
                .andExpect(jsonPath("$.total").value(7.70))
                .andExpect(jsonPath("$.pagamentos", hasSize(1)))
                .andExpect(jsonPath("$.troco").value(2.30));

        // O garçom pode ver a conta, mas a cozinha não
        mvc.perform(get("/api/v1/comandas/" + c.comanda + "/comprovante").session(c.garcom)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/comandas/" + c.comanda + "/comprovante").session(c.cozinha)).andExpect(status().isForbidden());
    }

    @Test
    void garcomECozinhaNaoMexemNoCaixa() throws Exception {
        Cenario c = montar();
        lancar(c.garcom, c.comanda, c.refri, 1);

        mvc.perform(post("/api/v1/caixa/turnos").with(csrf()).session(c.garcom).contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorInicial\":0}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/caixa/turno-atual").session(c.cozinha)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/pagamentos").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content(pagamento("PIX", "7.70", null))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/fechar").with(csrf()).session(c.garcom)).andExpect(status().isForbidden());
    }

    @Test
    void valoresInvalidosSaoRecusados() throws Exception {
        Cenario c = montar();
        lancar(c.garcom, c.comanda, c.refri, 1);
        abrirCaixa(c.caixa, "0");

        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/pagamentos").with(csrf()).session(c.caixa)
                .contentType(MediaType.APPLICATION_JSON).content(pagamento("PIX", "0", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.valor").exists());
        mvc.perform(post("/api/v1/comandas/" + c.comanda + "/pagamentos").with(csrf()).session(c.caixa)
                .contentType(MediaType.APPLICATION_JSON).content(pagamento("PIX", "1.999", null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/caixa/turnos").with(csrf()).session(c.gerente).contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorInicial\":-5}")).andExpect(status().isBadRequest());
    }
}
