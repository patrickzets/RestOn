package br.edu.reston.caixa.dto;

import java.math.BigDecimal;
import java.time.Instant;

import br.edu.reston.caixa.MetodoPagamento;
import br.edu.reston.caixa.OrigemPagamento;
import br.edu.reston.caixa.Pagamento;
import br.edu.reston.caixa.StatusPagamento;

public record PagamentoResposta(
        Long id,
        Long comandaId,
        Long reservaId,
        MetodoPagamento metodo,
        OrigemPagamento origem,
        BigDecimal valor,
        BigDecimal valorRecebido,
        BigDecimal troco,
        StatusPagamento status,
        Instant criadoEm) {

    public static PagamentoResposta de(Pagamento p) {
        return new PagamentoResposta(p.getId(), p.getComanda() == null ? null : p.getComanda().getId(), p.getReservaId(),
                p.getMetodo(), p.getOrigem(), p.getValor(), p.getValorRecebido(), p.getTroco(), p.getStatus(), p.getCriadoEm());
    }
}
