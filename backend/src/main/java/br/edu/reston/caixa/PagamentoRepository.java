package br.edu.reston.caixa;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    List<Pagamento> findByComandaIdOrderByCriadoEmAsc(Long comandaId);

    // Pagamentos antecipados de uma reserva que ainda não viraram crédito em nenhuma comanda
    List<Pagamento> findByReservaIdAndComandaIsNullAndStatus(Long reservaId, StatusPagamento status);

    List<Pagamento> findByReservaIdAndOrigemAndStatus(Long reservaId, OrigemPagamento origem, StatusPagamento status);

    // Quanto já foi pago (e não estornado) numa comanda
    @Query("select coalesce(sum(p.valor), 0) from Pagamento p where p.comanda.id = :comandaId "
            + "and p.status = br.edu.reston.caixa.StatusPagamento.CONFIRMADO")
    BigDecimal somaConfirmadaDaComanda(@Param("comandaId") Long comandaId);

    // Quanto foi pago antecipadamente por uma reserva
    @Query("select coalesce(sum(p.valor), 0) from Pagamento p where p.reservaId = :reservaId "
            + "and p.origem = br.edu.reston.caixa.OrigemPagamento.RESERVA "
            + "and p.status = br.edu.reston.caixa.StatusPagamento.CONFIRMADO")
    BigDecimal somaAntecipadaDaReserva(@Param("reservaId") Long reservaId);

    // Total recebido por forma de pagamento num turno: linhas [método, soma, quantidade]
    @Query("select p.metodo, coalesce(sum(p.valor), 0), count(p) from Pagamento p where p.turno.id = :turnoId "
            + "and p.status = br.edu.reston.caixa.StatusPagamento.CONFIRMADO group by p.metodo")
    List<Object[]> totaisDoTurno(@Param("turnoId") Long turnoId);
}
