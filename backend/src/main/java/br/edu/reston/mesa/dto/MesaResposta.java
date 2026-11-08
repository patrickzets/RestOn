package br.edu.reston.mesa.dto;

import java.time.Instant;

import br.edu.reston.atendimento.Atendimento;
import br.edu.reston.mesa.EstadoMesa;
import br.edu.reston.mesa.Mesa;
import br.edu.reston.reserva.Reserva;

public record MesaResposta(
        Long id,
        int numero,
        int capacidade,
        EstadoMesa estado,
        boolean ativa,
        Long atendimentoId,
        Instant atendimentoAbertoEm,
        ReservaProxima reservaProxima,
        int comandasAbertas,
        int pedidosProntos,
        String setor,
        Long abertoPor) {

    // Reserva que começa logo (ou já devia ter começado) nesta mesa
    public record ReservaProxima(Long id, String nomeCliente, int qtdPessoas, Instant inicio) {
    }

    public static MesaResposta de(Mesa m, Atendimento atendimento, Reserva reserva) {
        return de(m, atendimento, reserva, 0, 0);
    }

    public static MesaResposta de(Mesa m, Atendimento atendimento, Reserva reserva, int comandasAbertas, int pedidosProntos) {
        return new MesaResposta(
                m.getId(), m.getNumero(), m.getCapacidade(), m.getEstado(), m.isAtiva(),
                atendimento == null ? null : atendimento.getId(),
                atendimento == null ? null : atendimento.getAbertoEm(),
                reserva == null ? null
                        : new ReservaProxima(reserva.getId(), reserva.getNomeCliente(), reserva.getQtdPessoas(), reserva.getInicio()),
                comandasAbertas, pedidosProntos, m.getSetor(), atendimento == null ? null : atendimento.getAbertoPor());
    }
}
