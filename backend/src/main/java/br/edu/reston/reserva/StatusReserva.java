package br.edu.reston.reserva;

import java.util.List;

public enum StatusReserva {
    ATIVA,
    CHEGOU,
    CONCLUIDA,
    CANCELADA,
    NAO_COMPARECEU;

    // Só estas reservas seguram a mesa no horário (as outras já acabaram ou foram canceladas)
    public static final List<StatusReserva> QUE_SEGURAM_A_MESA = List.of(ATIVA, CHEGOU);
}
