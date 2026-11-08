package br.edu.reston.caixa.dto;

import java.math.BigDecimal;
import java.time.Instant;

import br.edu.reston.caixa.StatusTurno;

public record TurnoResposta(
        Long id,
        Long operadorId,
        String operadorNome,
        Instant abertoEm,
        Instant fechadoEm,
        BigDecimal valorInicial,
        BigDecimal valorContado,
        StatusTurno status,
        BigDecimal totalPix,
        BigDecimal totalCartao,
        BigDecimal totalDinheiro,
        BigDecimal totalRecebido,
        long qtdPagamentos,
        // Dinheiro que deveria estar na gaveta: valor inicial + o que entrou em dinheiro
        BigDecimal dinheiroEsperado,
        // contado - esperado (só depois de fechado): negativo = faltou, positivo = sobrou
        BigDecimal diferenca) {
}
