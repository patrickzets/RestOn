package br.edu.reston.estoque;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimentacaoRepository extends JpaRepository<MovimentacaoEstoque, Long> {

    List<MovimentacaoEstoque> findTop50ByProdutoIdOrderByCriadoEmDescIdDesc(Long produtoId);
}
