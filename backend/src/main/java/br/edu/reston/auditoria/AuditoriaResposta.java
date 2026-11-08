package br.edu.reston.auditoria;

import java.time.Instant;

public record AuditoriaResposta(
        Long id,
        Instant em,
        String login,
        String acao,
        String entidade,
        String entidadeId,
        String detalhes,
        String ip) {

    public static AuditoriaResposta de(AuditLog log) {
        return new AuditoriaResposta(log.getId(), log.getEm(), log.getLogin(), log.getAcao(), log.getEntidade(),
                log.getEntidadeId(), log.getDetalhes(), log.getIp());
    }
}
