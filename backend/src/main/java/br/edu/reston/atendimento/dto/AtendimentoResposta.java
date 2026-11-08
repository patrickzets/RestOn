package br.edu.reston.atendimento.dto;

import java.time.Instant;

import br.edu.reston.atendimento.Atendimento;

public record AtendimentoResposta(
        Long id,
        Long mesaId,
        int mesaNumero,
        Long reservaId,
        Instant abertoEm,
        Instant encerradoEm) {

    public static AtendimentoResposta de(Atendimento a) {
        return new AtendimentoResposta(a.getId(), a.getMesa().getId(), a.getMesa().getNumero(),
                a.getReserva() == null ? null : a.getReserva().getId(), a.getAbertoEm(), a.getEncerradoEm());
    }
}
