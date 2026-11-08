package br.edu.reston.estoque.dto;

import java.time.Instant;

import br.edu.reston.estoque.MovimentacaoEstoque;
import br.edu.reston.estoque.TipoMovimentacao;

public record MovimentacaoResposta(
        Long id,
        TipoMovimentacao tipo,
        int variacao,
        int saldoApos,
        String motivo,
        String funcionario,
        Instant criadoEm) {

    public static MovimentacaoResposta de(MovimentacaoEstoque m, String funcionario) {
        return new MovimentacaoResposta(m.getId(), m.getTipo(), m.getVariacao(), m.getSaldoApos(), m.getMotivo(), funcionario, m.getCriadoEm());
    }
}
