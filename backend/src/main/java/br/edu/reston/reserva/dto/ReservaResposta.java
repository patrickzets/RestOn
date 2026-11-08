package br.edu.reston.reserva.dto;

import java.math.BigDecimal;
import java.time.Instant;

import br.edu.reston.reserva.Reserva;
import br.edu.reston.reserva.StatusReserva;

public record ReservaResposta(
        Long id,
        Long mesaId,
        int mesaNumero,
        String nomeCliente,
        String telefone,
        int qtdPessoas,
        Instant inicio,
        Instant fim,
        StatusReserva status,
        boolean atrasada,
        BigDecimal valorAntecipado,
        Instant criadoEm) {

    // O telefone só sai para quem pode ver (por causa da LGPD); para os outros vai null
    public static ReservaResposta de(Reserva r, boolean mostrarTelefone, boolean atrasada, BigDecimal valorAntecipado) {
        return new ReservaResposta(r.getId(), r.getMesa().getId(), r.getMesa().getNumero(), r.getNomeCliente(),
                mostrarTelefone ? r.getTelefone() : null, r.getQtdPessoas(), r.getInicio(), r.getFim(),
                r.getStatus(), atrasada, valorAntecipado, r.getCriadoEm());
    }
}
