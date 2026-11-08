package br.edu.reston.cardapio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    List<Produto> findAllByOrderByNomeAsc();

    List<Produto> findAllByAtivoTrueOrderByNomeAsc();

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);

    boolean existsByCategoriaId(Long categoriaId);

    // Quantos produtos ativos tem cada categoria: linhas [id da categoria, quantidade]
    @Query("select p.categoria.id, count(p) from Produto p where p.ativo = true group by p.categoria.id")
    List<Object[]> contarAtivosPorCategoria();

    // Trava as linhas na ordem do id, para dois pedidos ao mesmo tempo não se bloquearem um ao outro
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Produto p where p.id in :ids order by p.id")
    List<Produto> travar(@Param("ids") List<Long> ids);
}
