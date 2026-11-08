package br.edu.reston.caixa.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import br.edu.reston.comanda.StatusComanda;

public record ComprovanteResposta(
        String codigo,
        StatusComanda status,
        int mesaNumero,
        int numeroCliente,
        String nomeCliente,
        Instant abertaEm,
        Instant fechadaEm,
        List<Linha> itens,
        BigDecimal subtotal,
        BigDecimal taxaPercentual,
        boolean taxaRemovida,
        BigDecimal taxaValor,
        BigDecimal total,
        List<PagamentoResposta> pagamentos,
        BigDecimal pago,
        BigDecimal troco) {

    public record Linha(String produto, int quantidade, BigDecimal precoUnitario, BigDecimal subtotal, String observacao) {
    }
}
