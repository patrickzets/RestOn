package br.edu.reston.comanda;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComandaRepository extends JpaRepository<Comanda, Long> {

    List<Comanda> findByAtendimentoIdOrderByNumeroClienteAsc(Long atendimentoId);

    List<Comanda> findAllByOrderByAbertaEmDesc(Pageable limite);

    boolean existsByAtendimentoIdAndStatus(Long atendimentoId, StatusComanda status);

    // Próximo "Cliente N" do atendimento
    @Query("select coalesce(max(c.numeroCliente), 0) from Comanda c where c.atendimento.id = :atendimentoId")
    int maiorNumeroDeCliente(@Param("atendimentoId") Long atendimentoId);

    // Código único vindo de uma sequência do banco (duas pessoas abrindo ao mesmo tempo nunca repetem)
    @Query(value = "select nextval('comanda_codigo_seq')", nativeQuery = true)
    long proximoNumeroDeCodigo();

    // Quantas comandas abertas tem cada mesa: linhas [id da mesa, quantidade]
    @Query("select c.atendimento.mesa.id, count(c) from Comanda c where c.status = br.edu.reston.comanda.StatusComanda.ABERTA "
            + "and c.atendimento.encerradoEm is null group by c.atendimento.mesa.id")
    List<Object[]> contarAbertasPorMesa();
}
