package br.edu.reston.relatorio;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import br.edu.reston.BaseIntegracaoTest;
import br.edu.reston.funcionario.Perfil;

class RelatorioIntegracaoTest extends BaseIntegracaoTest {

    @PersistenceContext
    private EntityManager gerenciador;

    private Integer id(MvcResult resultado, String caminho) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), caminho);
    }

    private record Cenario(MockHttpSession admin, MockHttpSession gerente, MockHttpSession caixa, MockHttpSession garcom,
            Integer atendimento, Integer refri) {
    }

    private Cenario montar() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession gerente = criarEEntrar(admin, "gil", Perfil.GERENTE);
        MockHttpSession caixa = criarEEntrar(admin, "caio", Perfil.CAIXA);
        MockHttpSession garcom = criarEEntrar(admin, "gabi", Perfil.GARCOM);
        Long mesa = criarMesa(admin, 1, 4);
        Integer categoria = id(mvc.perform(post("/api/v1/categorias").with(csrf()).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content(json("nome", "Bebidas"))).andReturn(), "$.id");
        Integer refri = id(mvc.perform(post("/api/v1/produtos").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoriaId\":" + categoria + ",\"nome\":\"Refrigerante\",\"preco\":7.00,\"tempoPreparoMin\":0,\"destinoPreparo\":\"SEM_PREPARO\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        Integer atendimento = id(mvc.perform(post("/api/v1/mesas/" + mesa + "/atendimentos").with(csrf()).session(garcom))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        return new Cenario(admin, gerente, caixa, garcom, atendimento, refri);
    }

    private Integer abrirComanda(Cenario c) throws Exception {
        return id(mvc.perform(post("/api/v1/atendimentos/" + c.atendimento + "/comandas").with(csrf()).session(c.garcom))
                .andExpect(status().isCreated()).andReturn(), "$.id");
    }

    // Pede refrigerantes na comanda, entrega, paga tudo e fecha
    private void venderEFechar(Cenario c, Integer comanda, int quantidade, String metodo, String valor) throws Exception {
        MvcResult pedido = mvc.perform(post("/api/v1/comandas/" + comanda + "/pedidos").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itens\":[{\"produtoId\":" + c.refri + ",\"quantidade\":" + quantidade + "}]}"))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(patch("/api/v1/pedidos/" + id(pedido, "$[0].id") + "/status").with(csrf()).session(c.garcom)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ENTREGUE\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/comandas/" + comanda + "/pagamentos").with(csrf()).session(c.caixa)
                .contentType(MediaType.APPLICATION_JSON).content("{\"metodo\":\"" + metodo + "\",\"valor\":" + valor + "}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/comandas/" + comanda + "/fechar").with(csrf()).session(c.caixa)).andExpect(status().isOk());
    }

    private void abrirCaixa(Cenario c) throws Exception {
        mvc.perform(post("/api/v1/caixa/turnos").with(csrf()).session(c.caixa).contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorInicial\":50}")).andExpect(status().isCreated());
    }

    @Test
    void semVendasOsNumerosSaoZeroENadaEInventado() throws Exception {
        Cenario c = montar();

        mvc.perform(get("/api/v1/painel").session(c.gerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faturamentoHoje").value(0))
                .andExpect(jsonPath("$.comandasFechadasHoje").value(0))
                .andExpect(jsonPath("$.ticketMedioHoje").value(0))
                .andExpect(jsonPath("$.operadorDoCaixa").doesNotExist())
                .andExpect(jsonPath("$.vendasPorHora", hasSize(0)))
                .andExpect(jsonPath("$.maisVendidosHoje", hasSize(0)));

        mvc.perform(get("/api/v1/relatorios/vendas").session(c.gerente))
                .andExpect(jsonPath("$.resumo.comandas").value(0))
                .andExpect(jsonPath("$.porDia", hasSize(7)))
                .andExpect(jsonPath("$.maisVendidos", hasSize(0)));
    }

    @Test
    void relatorioSomaSoComandasFechadasEAgrupaPorDiaMetodoProdutoEGarcom() throws Exception {
        Cenario c = montar();
        abrirCaixa(c);
        // As três comandas abrem antes: o atendimento só encerra quando a última comanda fecha
        Integer primeira = abrirComanda(c);
        Integer segunda = abrirComanda(c);
        Integer aberta = abrirComanda(c);
        venderEFechar(c, primeira, 2, "PIX", "15.40"); // 14,00 + 10%
        venderEFechar(c, segunda, 1, "DINHEIRO", "7.70");

        // Uma comanda aberta com pedido não entra no faturamento
        mvc.perform(post("/api/v1/comandas/" + aberta + "/pedidos").with(csrf()).session(c.garcom).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itens\":[{\"produtoId\":" + c.refri + ",\"quantidade\":5}]}")).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/relatorios/vendas").session(c.gerente))
                .andExpect(jsonPath("$.resumo.faturamento").value(23.10))
                .andExpect(jsonPath("$.resumo.comandas").value(2))
                .andExpect(jsonPath("$.resumo.ticketMedio").value(11.55))
                .andExpect(jsonPath("$.resumo.taxaDeServico").value(2.10))
                .andExpect(jsonPath("$.porDia", hasSize(7)))
                .andExpect(jsonPath("$.porDia[6].total").value(23.10))
                .andExpect(jsonPath("$.porDia[6].comandas").value(2))
                .andExpect(jsonPath("$.porDia[0].total").value(0))
                .andExpect(jsonPath("$.porMetodo", hasSize(2)))
                .andExpect(jsonPath("$.porMetodo[0].metodo").value("PIX"))
                .andExpect(jsonPath("$.porMetodo[0].total").value(15.40))
                .andExpect(jsonPath("$.maisVendidos[0].produto").value("Refrigerante"))
                .andExpect(jsonPath("$.maisVendidos[0].quantidade").value(3))
                .andExpect(jsonPath("$.maisVendidos[0].receita").value(21.00))
                .andExpect(jsonPath("$.porGarcom[0].garcom").value("Pessoa gabi"))
                .andExpect(jsonPath("$.porGarcom[0].total").value(23.10));

        mvc.perform(get("/api/v1/painel").session(c.gerente))
                .andExpect(jsonPath("$.faturamentoHoje").value(23.10))
                .andExpect(jsonPath("$.comandasFechadasHoje").value(2))
                .andExpect(jsonPath("$.comandasAbertas").value(1))
                .andExpect(jsonPath("$.mesasOcupadas").value(1))
                .andExpect(jsonPath("$.mesasAtivas").value(1))
                .andExpect(jsonPath("$.operadorDoCaixa").value("Pessoa caio"))
                .andExpect(jsonPath("$.vendasPorHora", hasSize(1)))
                .andExpect(jsonPath("$.maisVendidosHoje[0].quantidade").value(3));
    }

    @Test
    void operiodoPodeSerEscolhidoEEValidado() throws Exception {
        Cenario c = montar();

        mvc.perform(get("/api/v1/relatorios/vendas?de=2020-01-01&ate=2020-01-03").session(c.admin))
                .andExpect(jsonPath("$.porDia", hasSize(3)))
                .andExpect(jsonPath("$.resumo.faturamento").value(0));

        mvc.perform(get("/api/v1/relatorios/vendas?de=2026-02-10&ate=2026-02-01").session(c.admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("data inicial")));
        mvc.perform(get("/api/v1/relatorios/vendas?de=2020-01-01&ate=2022-01-01").session(c.admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("no máximo")));
    }

    @Test
    void painelContaProdutosComEstoqueBaixo() throws Exception {
        Cenario c = montar();
        mvc.perform(put("/api/v1/estoque/" + c.refri + "/controle").with(csrf()).session(c.gerente).contentType(MediaType.APPLICATION_JSON)
                .content("{\"controla\":true,\"minimo\":5,\"quantidadeInicial\":3}")).andExpect(status().isOk());
        gerenciador.flush();

        mvc.perform(get("/api/v1/painel").session(c.gerente)).andExpect(jsonPath("$.produtosComEstoqueBaixo").value(1));
    }

    @Test
    void soGerenteEAdministradorVeemOsNumeros() throws Exception {
        Cenario c = montar();

        mvc.perform(get("/api/v1/painel").session(c.garcom)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/painel").session(c.caixa)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/relatorios/vendas").session(c.garcom)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/relatorios/vendas").session(c.admin)).andExpect(status().isOk());
    }
}
