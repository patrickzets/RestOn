package br.edu.reston.cardapio;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.cardapio.dto.CategoriaRequest;
import br.edu.reston.cardapio.dto.CategoriaResposta;
import br.edu.reston.erro.ConflitoException;
import br.edu.reston.erro.NaoEncontradoException;
import br.edu.reston.erro.RegraDeNegocioException;

@Service
public class CategoriaService {

    private final CategoriaRepository repositorio;
    private final ProdutoRepository produtos;
    private final AuditoriaService auditoria;

    public CategoriaService(CategoriaRepository repositorio, ProdutoRepository produtos, AuditoriaService auditoria) {
        this.repositorio = repositorio;
        this.produtos = produtos;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<CategoriaResposta> listar() {
        Map<Long, Long> quantidades = new HashMap<>();
        for (Object[] linha : produtos.contarAtivosPorCategoria()) {
            quantidades.put((Long) linha[0], (Long) linha[1]);
        }
        return repositorio.findAllByOrderByOrdemAscNomeAsc().stream()
                .map(c -> CategoriaResposta.de(c, quantidades.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoriaResposta criar(CategoriaRequest dados, FuncionarioDetalhes autor) {
        String nome = dados.nome().trim();
        if (repositorio.existsByNomeIgnoreCase(nome)) {
            throw new ConflitoException("Já existe a categoria " + nome + ".");
        }
        int proximaOrdem = (int) repositorio.count();
        Categoria categoria = repositorio.save(new Categoria(nome, proximaOrdem));

        registrar(autor, "CATEGORIA_CRIADA", categoria, "nome=" + nome);
        return CategoriaResposta.de(categoria, 0);
    }

    @Transactional
    public CategoriaResposta renomear(Long id, CategoriaRequest dados, FuncionarioDetalhes autor) {
        Categoria categoria = buscar(id);
        String nome = dados.nome().trim();
        if (repositorio.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new ConflitoException("Já existe a categoria " + nome + ".");
        }
        String antes = categoria.getNome();
        categoria.setNome(nome);

        registrar(autor, "CATEGORIA_ALTERADA", categoria, antes + " -> " + nome);
        return CategoriaResposta.de(categoria, 0);
    }

    // direcao: -1 sobe uma posição, +1 desce uma posição
    @Transactional
    public List<CategoriaResposta> mover(Long id, int direcao, FuncionarioDetalhes autor) {
        List<Categoria> lista = repositorio.findAllByOrderByOrdemAscNomeAsc();
        int posicao = -1;
        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i).getId().equals(id)) posicao = i;
        }
        if (posicao < 0) {
            throw new NaoEncontradoException("Categoria não encontrada.");
        }

        int destino = posicao + direcao;
        if (destino >= 0 && destino < lista.size()) {
            Categoria movida = lista.remove(posicao);
            lista.add(destino, movida);
            // Renumera tudo de 0 em diante para a ordem nunca ficar repetida
            for (int i = 0; i < lista.size(); i++) {
                lista.get(i).setOrdem(i);
            }
            registrar(autor, "CATEGORIA_REORDENADA", movida, "nome=" + movida.getNome());
        }
        return listar();
    }

    @Transactional
    public void excluir(Long id, FuncionarioDetalhes autor) {
        Categoria categoria = buscar(id);
        if (produtos.existsByCategoriaId(id)) {
            throw new RegraDeNegocioException("Esta categoria tem produtos. Mude os produtos de categoria antes de excluir.");
        }
        repositorio.delete(categoria);
        registrar(autor, "CATEGORIA_EXCLUIDA", categoria, "nome=" + categoria.getNome());
    }

    private Categoria buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new NaoEncontradoException("Categoria não encontrada."));
    }

    private void registrar(FuncionarioDetalhes autor, String acao, Categoria categoria, String detalhes) {
        auditoria.registrar(autor.getId(), autor.getUsername(), acao, "categoria", String.valueOf(categoria.getId()), detalhes);
    }
}
