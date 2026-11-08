package br.edu.reston.reserva;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.configuracao.ConfiguracaoService;

// Tarefas automáticas das reservas. Rodam sozinhas, mas os métodos são públicos para os testes chamarem direto.
@Component
public class ReservaRotinas {

    private static final List<StatusReserva> ENCERRADAS =
            List.of(StatusReserva.CONCLUIDA, StatusReserva.CANCELADA, StatusReserva.NAO_COMPARECEU);

    private final ReservaRepository reservas;
    private final ConfiguracaoService configuracao;
    private final AuditoriaService auditoria;

    public ReservaRotinas(ReservaRepository reservas, ConfiguracaoService configuracao, AuditoriaService auditoria) {
        this.reservas = reservas;
        this.configuracao = configuracao;
        this.auditoria = auditoria;
    }

    // Reserva ativa cujo horário acabou sem o cliente chegar. O pagamento antecipado fica como está:
    // devolver (ou não) é decisão do gerente.
    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    @Transactional
    public int marcarNaoComparecimentos() {
        List<Reserva> perdidas = reservas.findByStatusAndFimBefore(StatusReserva.ATIVA, Instant.now());
        for (Reserva reserva : perdidas) {
            reserva.setStatus(StatusReserva.NAO_COMPARECEU);
            auditoria.registrar(null, "sistema", "RESERVA_NAO_COMPARECEU", "reserva", String.valueOf(reserva.getId()),
                    "mesa=" + reserva.getMesa().getNumero() + ", inicio=" + reserva.getInicio());
        }
        return perdidas.size();
    }

    // Apaga nome e telefone de reservas encerradas há mais tempo do que o prazo de guarda
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public int anonimizarReservasAntigas() {
        int dias = configuracao.minutos("lgpd_retencao_reservas_dias");
        Instant limite = Instant.now().minus(dias, ChronoUnit.DAYS);
        List<Reserva> antigas = reservas.encerradasAte(ENCERRADAS, limite);
        for (Reserva reserva : antigas) {
            reserva.anonimizar();
        }
        if (!antigas.isEmpty()) {
            auditoria.registrar(null, "sistema", "RESERVAS_ANONIMIZADAS", "reserva", null,
                    antigas.size() + " reserva(s) encerrada(s) há mais de " + dias + " dias");
        }
        return antigas.size();
    }
}
