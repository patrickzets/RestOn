package br.edu.reston.atendimento;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AtendimentoRepository extends JpaRepository<Atendimento, Long> {

    List<Atendimento> findByEncerradoEmIsNull();
}
