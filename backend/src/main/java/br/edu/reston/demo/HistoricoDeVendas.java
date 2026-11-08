package br.edu.reston.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.jdbc.core.JdbcTemplate;

// Grava direto no banco as vendas dos últimos dias (comandas já fechadas, com datas no passado).
// Os totais seguem a mesma conta do sistema: subtotal dos itens + 10% de taxa de serviço.
class HistoricoDeVendas {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final BigDecimal TAXA = new BigDecimal("10.00");
    private static final BigDecimal NOTA = new BigDecimal("10");

    private record ProdutoVenda(long id, String nome, BigDecimal preco, String destino) {
    }

    private record ItemVenda(ProdutoVenda produto, int quantidade) {
    }

    private final JdbcTemplate jdbc;
    private final Random sorteio = new Random(2026);
    private final List<ProdutoVenda> vitrine = new ArrayList<>();
    private final List<Long> mesas;
    private final List<Long> garcons;
    private final long caixaId;

    HistoricoDeVendas(JdbcTemplate jdbc, List<Long> mesas, List<Long> garcons, long caixaId, Map<String, Integer> pesos) {
        this.jdbc = jdbc;
        this.mesas = mesas;
        this.garcons = garcons;
        this.caixaId = caixaId;

        // Quanto maior o peso, mais vezes o produto aparece nas comandas
        for (ProdutoVenda p : jdbc.query("select id, nome, preco, destino_preparo from produto where ativo",
                (rs, n) -> new ProdutoVenda(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4)))) {
            for (int i = 0; i < pesos.getOrDefault(p.nome(), 1); i++) {
                vitrine.add(p);
            }
        }
    }

    // Um turno de caixa fechado por dia; em um deles falta um pouco de dinheiro no caixa
    void gerarDiasAnteriores(int dias, int diaComDiferenca, BigDecimal diferenca) {
        LocalDate hoje = LocalDate.now(FUSO);
        for (int d = dias; d >= 1; d--) {
            LocalDate dia = hoje.minusDays(d);
            Instant abertura = dia.atTime(11, 30).atZone(FUSO).toInstant();
            Instant fechamento = dia.atTime(23, 50).atZone(FUSO).toInstant();
            long turno = inserirTurno(abertura, fechamento);

            boolean movimentado = dia.getDayOfWeek() == DayOfWeek.FRIDAY || dia.getDayOfWeek() == DayOfWeek.SATURDAY
                    || dia.getDayOfWeek() == DayOfWeek.SUNDAY;
            int visitas = (movimentado ? 8 : 4) + sorteio.nextInt(3);

            BigDecimal dinheiro = BigDecimal.ZERO;
            for (int v = 0; v < visitas; v++) {
                Instant inicio = dia.atTime(12 + sorteio.nextInt(9), sorteio.nextInt(60)).atZone(FUSO).toInstant();
                int duracao = 50 + sorteio.nextInt(60);
                dinheiro = dinheiro.add(registrarVisita(inicio, duracao, turno, mesas.get(sorteio.nextInt(mesas.size()))));
            }

            BigDecimal esperado = new BigDecimal("200.00").add(dinheiro);
            BigDecimal contado = d == diaComDiferenca ? esperado.add(diferenca) : esperado;
            jdbc.update("update caixa_turno set valor_contado = ? where id = ?", contado, turno);
        }
    }

    // Vendas de hoje, entre a abertura do caixa e agora, no turno que está aberto
    void gerarHoje(long turnoAberto, Instant abertura, Instant agora, List<Long> mesasDeHoje) {
        long minutos = Duration.between(abertura, agora).toMinutes();
        int duracao = (int) Math.max(8, Math.min(75, minutos / 4));
        double[] posicoes = { 0.15, 0.40, 0.60 };
        for (int i = 0; i < posicoes.length; i++) {
            Instant inicio = abertura.plusSeconds((long) (minutos * 60 * posicoes[i]));
            registrarVisita(inicio, duracao, turnoAberto, mesasDeHoje.get(i % mesasDeHoje.size()));
        }
    }

    private long inserirTurno(Instant abertura, Instant fechamento) {
        return jdbc.queryForObject("insert into caixa_turno (operador_id, aberto_em, fechado_em, valor_inicial, status) "
                + "values (?, ?, ?, 200.00, 'FECHADO') returning id", Long.class, caixaId, ts(abertura), ts(fechamento));
    }

    // Uma mesa que sentou, consumiu e pagou. Devolve o total pago em dinheiro (para conferir o caixa).
    private BigDecimal registrarVisita(Instant inicio, int duracaoMin, long turno, long mesaId) {
        long duracao = duracaoMin * 60L;
        long garcom = garcons.get(sorteio.nextInt(garcons.size()));
        int comandas = 1 + (sorteio.nextInt(10) < 6 ? sorteio.nextInt(2) : 2);

        Instant ultimoFechamento = inicio;
        BigDecimal dinheiro = BigDecimal.ZERO;
        long atendimento = jdbc.queryForObject("insert into atendimento (mesa_id, aberto_por, aberto_em, encerrado_em) "
                + "values (?, ?, ?, ?) returning id", Long.class, mesaId, garcom, ts(inicio), ts(inicio.plusSeconds(duracao * 14 / 10)));

        for (int c = 1; c <= comandas; c++) {
            Instant aberta = inicio.plusSeconds(duracao * c / 20);
            Instant fechada = inicio.plusSeconds(duracao + duracao * c / 12);
            ultimoFechamento = fechada;
            dinheiro = dinheiro.add(registrarComanda(atendimento, c, garcom, turno, aberta, fechada));
        }
        jdbc.update("update atendimento set encerrado_em = ? where id = ?", ts(ultimoFechamento), atendimento);
        return dinheiro;
    }

    private BigDecimal registrarComanda(long atendimento, int numero, long garcom, long turno, Instant aberta, Instant fechada) {
        List<ItemVenda> itens = sortearItens();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemVenda item : itens) {
            subtotal = subtotal.add(item.produto().preco().multiply(BigDecimal.valueOf(item.quantidade())));
        }
        BigDecimal taxa = subtotal.multiply(TAXA).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(taxa);

        String codigo = String.format("C-%05d", jdbc.queryForObject("select nextval('comanda_codigo_seq')", Long.class));
        long comanda = jdbc.queryForObject("insert into comanda (codigo, atendimento_id, numero_cliente, status, garcom_id, "
                + "taxa_percentual, aberta_em, fechada_em, subtotal_final, taxa_valor_final, total_final) "
                + "values (?, ?, ?, 'FECHADA', ?, ?, ?, ?, ?, ?, ?) returning id", Long.class,
                codigo, atendimento, numero, garcom, TAXA, ts(aberta), ts(fechada), subtotal, taxa, total);

        registrarPedidos(comanda, itens, garcom, aberta, fechada);
        return registrarPagamentos(comanda, turno, total, fechada.minusSeconds(60));
    }

    private List<ItemVenda> sortearItens() {
        Map<Long, ItemVenda> escolhidos = new LinkedHashMap<>();
        int quantos = 2 + sorteio.nextInt(4);
        while (escolhidos.size() < quantos) {
            ProdutoVenda produto = vitrine.get(sorteio.nextInt(vitrine.size()));
            escolhidos.putIfAbsent(produto.id(), new ItemVenda(produto, 1 + sorteio.nextInt(3)));
        }
        return new ArrayList<>(escolhidos.values());
    }

    // Um pedido por destino de preparo, todos entregues; de vez em quando um item foi cancelado e não é cobrado
    private void registrarPedidos(long comanda, List<ItemVenda> itens, long garcom, Instant aberta, Instant fechada) {
        Map<String, List<ItemVenda>> porDestino = new LinkedHashMap<>();
        for (ItemVenda item : itens) {
            porDestino.computeIfAbsent(item.produto().destino(), d -> new ArrayList<>()).add(item);
        }

        long janela = Duration.between(aberta, fechada).toSeconds();
        int k = 0;
        for (Map.Entry<String, List<ItemVenda>> grupo : porDestino.entrySet()) {
            k++;
            String destino = grupo.getKey();
            Instant criado = aberta.plusSeconds(janela * k / 10);
            long pedido = jdbc.queryForObject("insert into pedido (comanda_id, destino, status, criado_por, criado_em) "
                    + "values (?, ?, 'ENTREGUE', ?, ?) returning id", Long.class, comanda, destino, garcom, ts(criado));

            for (ItemVenda item : grupo.getValue()) {
                inserirItem(pedido, comanda, item, false);
            }
            if (sorteio.nextInt(25) == 0) {
                inserirItem(pedido, comanda, new ItemVenda(vitrine.get(sorteio.nextInt(vitrine.size())), 1), true);
            }

            long passo = janela / 12;
            if (destino.equals("SEM_PREPARO")) {
                historico(pedido, null, "PRONTO", garcom, criado);
            } else {
                historico(pedido, null, "RECEBIDO", garcom, criado);
                historico(pedido, "RECEBIDO", "EM_PREPARO", garcom, criado.plusSeconds(passo));
                historico(pedido, "EM_PREPARO", "PRONTO", garcom, criado.plusSeconds(passo * 3));
            }
            historico(pedido, "PRONTO", "ENTREGUE", garcom, criado.plusSeconds(passo * 4));
        }
    }

    private void inserirItem(long pedido, long comanda, ItemVenda item, boolean cancelado) {
        jdbc.update("insert into item_pedido (pedido_id, comanda_id, produto_id, quantidade, preco_unitario, cancelado, motivo_cancelamento) "
                + "values (?, ?, ?, ?, ?, ?, ?)", pedido, comanda, item.produto().id(), item.quantidade(), item.produto().preco(),
                cancelado, cancelado ? "Cliente desistiu do item" : null);
    }

    private void historico(long pedido, String de, String para, long funcionario, Instant em) {
        jdbc.update("insert into pedido_historico (pedido_id, status_anterior, status_novo, funcionario_id, em) values (?, ?, ?, ?, ?)",
                pedido, de, para, funcionario, ts(em));
    }

    // Paga tudo de uma vez ou divide em dois pagamentos. Devolve a parte paga em dinheiro.
    private BigDecimal registrarPagamentos(long comanda, long turno, BigDecimal total, Instant quando) {
        String[] metodos = { "PIX", "CARTAO", "CARTAO", "DINHEIRO", "PIX" };
        String primeiro = metodos[sorteio.nextInt(metodos.length)];

        if (sorteio.nextInt(10) < 7) {
            return pagar(comanda, turno, primeiro, total, quando);
        }
        BigDecimal metade = total.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        String segundo = primeiro.equals("PIX") ? "DINHEIRO" : "PIX";
        BigDecimal dinheiro = pagar(comanda, turno, primeiro, metade, quando);
        return dinheiro.add(pagar(comanda, turno, segundo, total.subtract(metade), quando));
    }

    private BigDecimal pagar(long comanda, long turno, String metodo, BigDecimal valor, Instant quando) {
        BigDecimal recebido = valor;
        if (metodo.equals("DINHEIRO")) {
            // O cliente entrega notas redondas e recebe troco
            recebido = valor.divide(NOTA, 0, RoundingMode.CEILING).multiply(NOTA);
            if (recebido.compareTo(valor) == 0) {
                recebido = valor;
            }
        }
        jdbc.update("insert into pagamento (comanda_id, turno_id, metodo, origem, valor, valor_recebido, troco, status, criado_por, criado_em) "
                + "values (?, ?, ?, 'CAIXA', ?, ?, ?, 'CONFIRMADO', ?, ?)",
                comanda, turno, metodo, valor, recebido, recebido.subtract(valor), caixaId, ts(quando));
        return metodo.equals("DINHEIRO") ? valor : BigDecimal.ZERO;
    }

    private Timestamp ts(Instant instante) {
        return Timestamp.from(instante);
    }
}
