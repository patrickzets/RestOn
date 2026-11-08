package br.edu.reston.relatorio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Formato das respostas do painel e dos relatórios
public final class Relatorios {

    private Relatorios() {
    }

    public record ProdutoVendido(String produto, String categoria, long quantidade, BigDecimal receita) {
    }

    public record VendaPorHora(int hora, BigDecimal total) {
    }

    public record Painel(
            BigDecimal faturamentoHoje,
            long comandasFechadasHoje,
            BigDecimal ticketMedioHoje,
            long comandasAbertas,
            long mesasOcupadas,
            long mesasAtivas,
            long pedidosEmAndamento,
            long produtosComEstoqueBaixo,
            String operadorDoCaixa, // nulo quando o caixa está fechado
            List<VendaPorHora> vendasPorHora,
            List<ProdutoVendido> maisVendidosHoje) {
    }

    public record Resumo(
            BigDecimal faturamento,
            long comandas,
            BigDecimal ticketMedio,
            BigDecimal taxaDeServico,
            BigDecimal estornado,
            long itensCancelados) {
    }

    public record VendaPorDia(LocalDate data, BigDecimal total, long comandas) {
    }

    public record RecebidoPorMetodo(String metodo, BigDecimal total, long pagamentos) {
    }

    public record VendaPorGarcom(String garcom, BigDecimal total, long comandas) {
    }

    public record Vendas(
            LocalDate de,
            LocalDate ate,
            Resumo resumo,
            List<VendaPorDia> porDia,
            List<RecebidoPorMetodo> porMetodo,
            List<ProdutoVendido> maisVendidos,
            List<VendaPorGarcom> porGarcom) {
    }
}
