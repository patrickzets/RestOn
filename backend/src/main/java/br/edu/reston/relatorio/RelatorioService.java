package br.edu.reston.relatorio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.reston.erro.RegraDeNegocioException;
import br.edu.reston.relatorio.Relatorios.Painel;
import br.edu.reston.relatorio.Relatorios.ProdutoVendido;
import br.edu.reston.relatorio.Relatorios.RecebidoPorMetodo;
import br.edu.reston.relatorio.Relatorios.Resumo;
import br.edu.reston.relatorio.Relatorios.VendaPorDia;
import br.edu.reston.relatorio.Relatorios.VendaPorGarcom;
import br.edu.reston.relatorio.Relatorios.VendaPorHora;
import br.edu.reston.relatorio.Relatorios.Vendas;

@Service
public class RelatorioService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final int MAXIMO_DE_DIAS = 366;

    // O faturamento conta comandas fechadas (total gravado no fechamento), no dia em que fecharam
    private static final String COMANDA_NO_PERIODO = "c.status = 'FECHADA' and c.fechada_em >= ? and c.fechada_em < ?";

    private final JdbcTemplate jdbc;

    public RelatorioService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Painel painel() {
        LocalDate hoje = LocalDate.now(FUSO);
        Timestamp inicio = inicioDoDia(hoje);
        Timestamp fim = inicioDoDia(hoje.plusDays(1));

        Resumo resumo = resumo(inicio, fim);
        List<String> operador = jdbc.queryForList(
                "select f.nome from caixa_turno t join funcionario f on f.id = t.operador_id where t.status = 'ABERTO'", String.class);

        return new Painel(
                resumo.faturamento(),
                resumo.comandas(),
                resumo.ticketMedio(),
                contar("select count(*) from comanda where status = 'ABERTA'"),
                contar("select count(*) from atendimento where encerrado_em is null"),
                contar("select count(*) from mesa where ativa"),
                contar("select count(*) from pedido where status in ('RECEBIDO', 'EM_PREPARO', 'PRONTO')"),
                contar("select count(*) from produto where ativo and controla_estoque and estoque_atual <= estoque_minimo"),
                operador.isEmpty() ? null : operador.get(0),
                vendasPorHora(inicio, fim),
                maisVendidos(inicio, fim, 5));
    }

    @Transactional(readOnly = true)
    public Vendas vendas(LocalDate de, LocalDate ate) {
        LocalDate hoje = LocalDate.now(FUSO);
        LocalDate fim = ate == null ? hoje : ate;
        LocalDate inicio = de == null ? fim.minusDays(6) : de;

        if (inicio.isAfter(fim)) {
            throw new RegraDeNegocioException("A data inicial não pode ser depois da data final.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) >= MAXIMO_DE_DIAS) {
            throw new RegraDeNegocioException("Escolha um período de no máximo " + MAXIMO_DE_DIAS + " dias.");
        }

        Timestamp de0 = inicioDoDia(inicio);
        Timestamp ate0 = inicioDoDia(fim.plusDays(1));
        return new Vendas(inicio, fim, resumo(de0, ate0), porDia(inicio, fim, de0, ate0), porMetodo(de0, ate0),
                maisVendidos(de0, ate0, 10), porGarcom(de0, ate0));
    }

    private Resumo resumo(Timestamp inicio, Timestamp fim) {
        return jdbc.query("select coalesce(sum(c.total_final), 0), count(*), coalesce(sum(c.taxa_valor_final), 0) from comanda c where "
                + COMANDA_NO_PERIODO, rs -> {
                    rs.next();
                    BigDecimal faturamento = rs.getBigDecimal(1);
                    long comandas = rs.getLong(2);
                    BigDecimal ticket = comandas == 0 ? BigDecimal.ZERO : faturamento.divide(BigDecimal.valueOf(comandas), 2, RoundingMode.HALF_UP);
                    return new Resumo(faturamento, comandas, ticket, rs.getBigDecimal(3), estornado(inicio, fim), itensCancelados(inicio, fim));
                }, inicio, fim);
    }

    private BigDecimal estornado(Timestamp inicio, Timestamp fim) {
        return jdbc.queryForObject("select coalesce(sum(valor), 0) from estorno where em >= ? and em < ?", BigDecimal.class, inicio, fim);
    }

    private long itensCancelados(Timestamp inicio, Timestamp fim) {
        return jdbc.queryForObject("select count(*) from item_pedido i join pedido p on p.id = i.pedido_id "
                + "where i.cancelado and p.criado_em >= ? and p.criado_em < ?", Long.class, inicio, fim);
    }

    private List<VendaPorHora> vendasPorHora(Timestamp inicio, Timestamp fim) {
        return jdbc.query("select extract(hour from c.fechada_em at time zone 'America/Sao_Paulo')::int as hora, sum(c.total_final) "
                + "from comanda c where " + COMANDA_NO_PERIODO + " group by hora order by hora",
                (rs, n) -> new VendaPorHora(rs.getInt(1), rs.getBigDecimal(2)), inicio, fim);
    }

    // Todos os dias do período aparecem, inclusive os sem venda
    private List<VendaPorDia> porDia(LocalDate inicio, LocalDate fim, Timestamp de, Timestamp ate) {
        Map<LocalDate, VendaPorDia> vendidos = jdbc.query(
                "select (c.fechada_em at time zone 'America/Sao_Paulo')::date as dia, sum(c.total_final), count(*) "
                        + "from comanda c where " + COMANDA_NO_PERIODO + " group by dia",
                (rs, n) -> new VendaPorDia(rs.getObject(1, LocalDate.class), rs.getBigDecimal(2), rs.getLong(3)), de, ate)
                .stream().collect(Collectors.toMap(VendaPorDia::data, Function.identity()));

        List<VendaPorDia> dias = new ArrayList<>();
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            dias.add(vendidos.getOrDefault(dia, new VendaPorDia(dia, BigDecimal.ZERO, 0)));
        }
        return dias;
    }

    private List<RecebidoPorMetodo> porMetodo(Timestamp inicio, Timestamp fim) {
        return jdbc.query("select metodo, sum(valor), count(*) from pagamento where status = 'CONFIRMADO' "
                + "and criado_em >= ? and criado_em < ? group by metodo order by sum(valor) desc",
                (rs, n) -> new RecebidoPorMetodo(rs.getString(1), rs.getBigDecimal(2), rs.getLong(3)), inicio, fim);
    }

    private List<ProdutoVendido> maisVendidos(Timestamp inicio, Timestamp fim, int limite) {
        return jdbc.query("select pr.nome, ca.nome, sum(i.quantidade) as qtd, sum(i.quantidade * i.preco_unitario) as receita "
                + "from item_pedido i join comanda c on c.id = i.comanda_id join produto pr on pr.id = i.produto_id "
                + "join categoria ca on ca.id = pr.categoria_id where not i.cancelado and " + COMANDA_NO_PERIODO
                + " group by pr.id, pr.nome, ca.nome order by qtd desc, receita desc, pr.nome limit " + limite,
                (rs, n) -> new ProdutoVendido(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getBigDecimal(4)), inicio, fim);
    }

    private List<VendaPorGarcom> porGarcom(Timestamp inicio, Timestamp fim) {
        return jdbc.query("select f.nome, sum(c.total_final), count(*) from comanda c join funcionario f on f.id = c.garcom_id "
                + "where " + COMANDA_NO_PERIODO + " group by f.id, f.nome order by sum(c.total_final) desc",
                (rs, n) -> new VendaPorGarcom(rs.getString(1), rs.getBigDecimal(2), rs.getLong(3)), inicio, fim);
    }

    private long contar(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private Timestamp inicioDoDia(LocalDate dia) {
        return Timestamp.from(dia.atStartOfDay(FUSO).toInstant());
    }
}
