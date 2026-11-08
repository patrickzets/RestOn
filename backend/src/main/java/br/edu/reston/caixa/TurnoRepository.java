package br.edu.reston.caixa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurnoRepository extends JpaRepository<CaixaTurno, Long> {

    Optional<CaixaTurno> findByStatus(StatusTurno status);

    List<CaixaTurno> findTop30ByOrderByAbertoEmDesc();
}
