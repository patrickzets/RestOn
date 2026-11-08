package br.edu.reston.funcionario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FuncionarioRepository extends JpaRepository<Funcionario, Long> {

    Optional<Funcionario> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);

    List<Funcionario> findAllByOrderByNomeAsc();

    // Quantos têm o perfil, estão ativos e não estão bloqueados (usado para nunca ficar sem administrador)
    long countByPerfilAndAtivoTrueAndBloqueadoFalse(Perfil perfil);
}
