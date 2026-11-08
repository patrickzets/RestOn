package br.edu.reston.mesa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MesaRepository extends JpaRepository<Mesa, Long> {

    List<Mesa> findAllByOrderByNumeroAsc();

    boolean existsByNumero(int numero);

    boolean existsByNumeroAndIdNot(int numero, Long id);
}
