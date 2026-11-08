package br.edu.reston.reserva;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    // Reservas que começam dentro do período, da mais cedo para a mais tarde
    @Query("select r from Reserva r where r.inicio >= :de and r.inicio < :ate order by r.inicio")
    List<Reserva> doPeriodo(@Param("de") Instant de, @Param("ate") Instant ate);

    // Reservas ativas que já começaram ou começam logo (aparecem no painel de mesas)
    @Query("select r from Reserva r where r.status = br.edu.reston.reserva.StatusReserva.ATIVA "
            + "and r.fim > :agora and r.inicio < :limite order by r.inicio")
    List<Reserva> ativasAte(@Param("agora") Instant agora, @Param("limite") Instant limite);

    // A mesa já tem reserva que se cruza com o horário? (ignorarId serve para editar sem conflitar consigo mesma)
    @Query("select count(r) > 0 from Reserva r where r.mesa.id = :mesaId and r.status in :status "
            + "and r.inicio < :fim and r.fim > :inicio and r.id <> :ignorarId")
    boolean existeConflito(@Param("mesaId") Long mesaId, @Param("status") Collection<StatusReserva> status,
            @Param("inicio") Instant inicio, @Param("fim") Instant fim, @Param("ignorarId") Long ignorarId);

    // Ids das mesas que já têm reserva se cruzando com o horário
    @Query("select r.mesa.id from Reserva r where r.status in :status "
            + "and r.inicio < :fim and r.fim > :inicio and r.id <> :ignorarId")
    List<Long> mesasOcupadasNoHorario(@Param("status") Collection<StatusReserva> status,
            @Param("inicio") Instant inicio, @Param("fim") Instant fim, @Param("ignorarId") Long ignorarId);

    List<Reserva> findByStatusAndFimBefore(StatusReserva status, Instant limite);

    // Reservas encerradas há tempo suficiente e que ainda guardam dados pessoais
    @Query("select r from Reserva r where r.status in :status and r.fim < :limite and r.anonimizadoEm is null")
    List<Reserva> encerradasAte(@Param("status") Collection<StatusReserva> status, @Param("limite") Instant limite);

    boolean existsByMesaIdAndStatusAndFimAfter(Long mesaId, StatusReserva status, Instant agora);
}
