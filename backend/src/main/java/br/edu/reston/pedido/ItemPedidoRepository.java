package br.edu.reston.pedido;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemPedidoRepository extends JpaRepository<ItemPedido, Long> {

    // Itens cobrados nesta comanda (cancelados ficam de fora do total)
    List<ItemPedido> findByComandaIdAndCanceladoFalse(Long comandaId);

    boolean existsByComandaIdAndCanceladoFalse(Long comandaId);

    // Há item desta comanda cujo pedido está num dos status informados? (por exemplo, ainda não entregue)
    boolean existsByComandaIdAndCanceladoFalseAndPedidoStatusIn(Long comandaId, Collection<StatusPedido> status);
}
