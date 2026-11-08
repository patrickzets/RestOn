package br.edu.reston.comanda.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import br.edu.reston.caixa.dto.PagamentoResposta;
import br.edu.reston.comanda.Comanda;
import br.edu.reston.comanda.StatusComanda;
import br.edu.reston.pedido.dto.PedidoResposta;

public record ComandaResposta(
        Long id,
        String codigo,
        Long atendimentoId,
        Long mesaId,
        int mesaNumero,
        int numeroCliente,
        String nomeCliente,
        StatusComanda status,
        BigDecimal taxaPercentual,
        boolean taxaRemovida,
        BigDecimal subtotal,
        BigDecimal taxaValor,
        BigDecimal total,
        BigDecimal pago,
        BigDecimal restante,
        Instant abertaEm,
        Instant fechadaEm,
        List<PedidoResposta> pedidos, // só vêm preenchidos no detalhe da comanda
        List<PagamentoResposta> pagamentos) {

    public static ComandaResposta de(Comanda c, BigDecimal subtotal, BigDecimal taxaValor, BigDecimal pago,
            List<PedidoResposta> pedidos, List<PagamentoResposta> pagamentos) {
        BigDecimal total = subtotal.add(taxaValor);
        BigDecimal restante = total.subtract(pago).max(BigDecimal.ZERO);
        return new ComandaResposta(c.getId(), c.getCodigo(), c.getAtendimento().getId(), c.getAtendimento().getMesa().getId(),
                c.getAtendimento().getMesa().getNumero(), c.getNumeroCliente(), c.getNomeCliente(), c.getStatus(),
                c.getTaxaPercentual(), c.isTaxaRemovida(), subtotal, taxaValor, total, pago, restante,
                c.getAbertaEm(), c.getFechadaEm(), pedidos, pagamentos);
    }
}
