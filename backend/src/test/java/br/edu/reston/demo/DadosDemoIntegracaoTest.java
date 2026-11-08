package br.edu.reston.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import com.jayway.jsonpath.JsonPath;

import br.edu.reston.BaseIntegracaoTest;

// Liga o modo demonstração (num banco só deste teste) e confere o que ele criou
@SpringBootTest(properties = { "reston.admin.senha=" + BaseIntegracaoTest.SENHA_ADMIN,
        "reston.upload.diretorio=target/uploads-teste-demo", "reston.demo.ativo=true" })
class DadosDemoIntegracaoTest extends BaseIntegracaoTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DadosDemo dadosDemo;

    private long contar(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private BigDecimal somar(String sql) {
        return jdbc.queryForObject(sql, BigDecimal.class);
    }

    private BigDecimal numero(String json, String caminho) {
        Object valor = JsonPath.read(json, caminho);
        return new BigDecimal(valor.toString());
    }

    @Test
    void criaOsDadosDeDemonstracao() {
        // admin + os 6 funcionários de demonstração, um de cada perfil que a operação usa
        assertThat(contar("select count(*) from funcionario")).isEqualTo(7);
        assertThat(contar("select count(distinct perfil) from funcionario")).isEqualTo(6);
        assertThat(contar("select count(*) from funcionario where perfil = 'GARCOM'")).isEqualTo(2);
        assertThat(contar("select count(*) from funcionario where senha_provisoria")).isZero();

        assertThat(contar("select count(*) from mesa")).isEqualTo(6);
        assertThat(contar("select count(*) from categoria")).isEqualTo(5);
        assertThat(contar("select count(*) from produto")).isEqualTo(22);
        assertThat(contar("select count(*) from produto p join categoria c on c.id = p.categoria_id "
                + "where c.nome = 'Drinques' and p.destino_preparo <> 'BAR'")).isZero();
        assertThat(contar("select count(distinct destino_preparo) from produto")).isEqualTo(3);
        assertThat(contar("select count(*) from produto where imagem is not null")).isGreaterThan(5);

        assertThat(contar("select count(*) from produto where controla_estoque")).isEqualTo(6);
        assertThat(contar("select count(*) from produto where controla_estoque and estoque_atual = 0")).isEqualTo(1);
        assertThat(contar("select count(*) from produto where controla_estoque and estoque_atual > 0 and estoque_atual <= estoque_minimo"))
                .isEqualTo(1);

        assertThat(contar("select count(*) from reserva where status = 'ATIVA' and inicio > now()")).isGreaterThanOrEqualTo(6);
    }

    @Test
    void criaOEstadoAoVivo() {
        assertThat(contar("select count(*) from mesa where estado = 'OCUPADA'")).isEqualTo(2);
        assertThat(contar("select count(*) from atendimento where encerrado_em is null")).isEqualTo(2);
        assertThat(contar("select count(*) from comanda where status = 'ABERTA'")).isEqualTo(3);
        assertThat(contar("select count(*) from caixa_turno where status = 'ABERTO'")).isEqualTo(1);

        for (String status : List.of("RECEBIDO", "EM_PREPARO", "PRONTO")) {
            assertThat(contar("select count(*) from pedido where status = '" + status + "'")).isPositive();
        }
        assertThat(contar("select count(*) from pedido where destino = 'COZINHA' and status in ('RECEBIDO', 'EM_PREPARO', 'PRONTO')")).isPositive();
        assertThat(contar("select count(*) from pedido where destino = 'BAR' and status in ('RECEBIDO', 'EM_PREPARO', 'PRONTO')")).isPositive();

        // Um pedido em preparo já passou do tempo previsto do item mais lento
        assertThat(contar("select count(*) from pedido p where p.status = 'EM_PREPARO' and p.criado_em < now() - "
                + "(select max(pr.tempo_preparo_min) from item_pedido i join produto pr on pr.id = i.produto_id where i.pedido_id = p.id) "
                + "* interval '1 minute'")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void historicoTemTurnosFechadosEComandasCoerentes() {
        assertThat(contar("select count(*) from caixa_turno where status = 'FECHADO'")).isEqualTo(10);
        // Em um dos turnos o dinheiro contado não bate com o esperado
        assertThat(contar("select count(*) from caixa_turno t where t.status = 'FECHADO' and t.valor_contado <> t.valor_inicial + "
                + "coalesce((select sum(p.valor) from pagamento p where p.turno_id = t.id and p.metodo = 'DINHEIRO'), 0)")).isEqualTo(1);
        assertThat(contar("select count(distinct metodo) from pagamento")).isEqualTo(3);
        assertThat(contar("select count(distinct (fechada_em at time zone 'America/Sao_Paulo')::date) from comanda where status = 'FECHADA'"))
                .isGreaterThanOrEqualTo(10);

        // Total da comanda = itens + 10% de taxa, e o que foi pago cobre o total
        assertThat(contar("select count(*) from comanda c where c.status = 'FECHADA' and c.total_final <> c.subtotal_final + c.taxa_valor_final")).isZero();
        assertThat(contar("select count(*) from comanda c where c.status = 'FECHADA' and c.subtotal_final <> "
                + "(select coalesce(sum(i.quantidade * i.preco_unitario), 0) from item_pedido i where i.comanda_id = c.id and not i.cancelado)")).isZero();
        assertThat(contar("select count(*) from comanda c where c.status = 'FECHADA' and c.taxa_valor_final <> round(c.subtotal_final * 0.10, 2)")).isZero();
        assertThat(contar("select count(*) from comanda c where c.status = 'FECHADA' and c.total_final <> "
                + "(select coalesce(sum(p.valor), 0) from pagamento p where p.comanda_id = c.id and p.status = 'CONFIRMADO')")).isZero();
    }

    @Test
    void painelERelatorioBatemComAsComandasFechadas() throws Exception {
        MockHttpSession gerente = entrar("gerente", DadosDemo.SENHA_DEMO);

        String painel = mvc.perform(get("/api/v1/painel").session(gerente)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        BigDecimal faturamentoHoje = somar("select coalesce(sum(total_final), 0) from comanda where status = 'FECHADA' "
                + "and (fechada_em at time zone 'America/Sao_Paulo')::date = (now() at time zone 'America/Sao_Paulo')::date");
        assertThat(numero(painel, "$.faturamentoHoje")).isPositive().isEqualByComparingTo(faturamentoHoje);
        assertThat(numero(painel, "$.comandasFechadasHoje")).isPositive();
        assertThat(numero(painel, "$.comandasAbertas")).isEqualByComparingTo("3");
        assertThat(numero(painel, "$.mesasOcupadas")).isEqualByComparingTo("2");
        assertThat(numero(painel, "$.mesasAtivas")).isEqualByComparingTo("6");
        assertThat(numero(painel, "$.pedidosEmAndamento")).isPositive();
        assertThat(numero(painel, "$.produtosComEstoqueBaixo")).isEqualByComparingTo("2");
        assertThat((String) JsonPath.read(painel, "$.operadorDoCaixa")).isEqualTo("Carlos Mendes");

        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        String vendas = mvc.perform(get("/api/v1/relatorios/vendas").param("de", hoje.minusDays(10).toString()).param("ate", hoje.toString())
                .session(gerente)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        BigDecimal faturamentoTotal = somar("select coalesce(sum(total_final), 0) from comanda where status = 'FECHADA'");
        assertThat(numero(vendas, "$.resumo.faturamento")).isPositive().isEqualByComparingTo(faturamentoTotal);
        assertThat(numero(vendas, "$.resumo.comandas")).isEqualByComparingTo(String.valueOf(contar("select count(*) from comanda where status = 'FECHADA'")));

        List<Number> totaisPorDia = JsonPath.read(vendas, "$.porDia[*].total");
        assertThat(totaisPorDia).hasSize(11);
        BigDecimal somaDosDias = totaisPorDia.stream().map(n -> new BigDecimal(n.toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somaDosDias).isEqualByComparingTo(faturamentoTotal);
        assertThat(totaisPorDia.stream().filter(n -> n.doubleValue() > 0)).hasSize(11);

        List<String> metodos = JsonPath.read(vendas, "$.porMetodo[*].metodo");
        assertThat(metodos).containsExactlyInAnyOrder("PIX", "CARTAO", "DINHEIRO");
        List<Object> maisVendidos = JsonPath.read(vendas, "$.maisVendidos");
        assertThat(maisVendidos).isNotEmpty();
    }

    @Test
    void osFuncionariosDemoEntramSemTrocarASenha() throws Exception {
        for (String login : List.of("gerente", "garcom1", "garcom2", "caixa", "cozinha", "bartender")) {
            MockHttpSession sessao = entrar(login, DadosDemo.SENHA_DEMO);
            assertThat(sessao).isNotNull();
        }
    }

    @Test
    void rodarDeNovoNaoDuplicaNada() {
        long funcionarios = contar("select count(*) from funcionario");
        long produtos = contar("select count(*) from produto");
        long comandas = contar("select count(*) from comanda");
        long pedidos = contar("select count(*) from pedido");
        long reservas = contar("select count(*) from reserva");
        long pagamentos = contar("select count(*) from pagamento");

        dadosDemo.popular();
        dadosDemo.popular();

        assertThat(contar("select count(*) from funcionario")).isEqualTo(funcionarios);
        assertThat(contar("select count(*) from produto")).isEqualTo(produtos);
        assertThat(contar("select count(*) from comanda")).isEqualTo(comandas);
        assertThat(contar("select count(*) from pedido")).isEqualTo(pedidos);
        assertThat(contar("select count(*) from reserva")).isEqualTo(reservas);
        assertThat(contar("select count(*) from pagamento")).isEqualTo(pagamentos);
    }
}
