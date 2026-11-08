package br.edu.reston.pedido;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    // Pedidos em andamento para o painel da cozinha (do mais antigo para o mais novo)
    @Query("select distinct p from Pedido p join fetch p.itens join fetch p.comanda c join fetch c.atendimento a join fetch a.mesa "
            + "where p.status in :status order by p.criadoEm")
    List<Pedido> comStatus(@Param("status") Collection<StatusPedido> status);

    // Pedidos de uma comanda, com os itens (mais antigos primeiro)
    @Query("select distinct p from Pedido p join fetch p.itens where p.comanda.id = :comandaId order by p.criadoEm")
    List<Pedido> daComanda(@Param("comandaId") Long comandaId);

    // Pedidos de um atendimento que já estão prontos: linhas [id da mesa, quantidade]
    @Query("select p.comanda.atendimento.mesa.id, count(p) from Pedido p "
            + "where p.status = br.edu.reston.pedido.StatusPedido.PRONTO and p.comanda.atendimento.encerradoEm is null "
            + "group by p.comanda.atendimento.mesa.id")
    List<Object[]> contarProntosPorMesa();
}
