package br.edu.reston.cardapio;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.cardapio.dto.ProdutoRequest;
import br.edu.reston.cardapio.dto.ProdutoResposta;
import br.edu.reston.erro.ConflitoException;
import br.edu.reston.erro.NaoEncontradoException;
import br.edu.reston.funcionario.Perfil;

@Service
public class ProdutoService {

    private final ProdutoRepository repositorio;
    private final CategoriaRepository categorias;
    private final ArmazenamentoDeImagens imagens;
    private final AuditoriaService auditoria;

    public ProdutoService(ProdutoRepository repositorio, CategoriaRepository categorias, ArmazenamentoDeImagens imagens,
            AuditoriaService auditoria) {
        this.repositorio = repositorio;
        this.categorias = categorias;
        this.imagens = imagens;
        this.auditoria = auditoria;
    }

    // Quem gerencia vê também os arquivados; garçom e caixa só veem o que está no cardápio
    @Transactional(readOnly = true)
    public List<ProdutoResposta> listar(FuncionarioDetalhes quemPede) {
        boolean gerencia = quemPede.getPerfil() == Perfil.ADMIN || quemPede.getPerfil() == Perfil.GERENTE;
        List<Produto> lista = gerencia ? repositorio.findAllByOrderByNomeAsc() : repositorio.findAllByAtivoTrueOrderByNomeAsc();
        return lista.stream().map(ProdutoResposta::de).toList();
    }

    @Transactional
    public ProdutoResposta criar(ProdutoRequest dados, FuncionarioDetalhes autor) {
        String nome = dados.nome().trim();
        if (repositorio.existsByNomeIgnoreCase(nome)) {
            throw new ConflitoException("Já existe um produto chamado " + nome + ".");
        }
        Produto produto = new Produto(buscarCategoria(dados.categoriaId()), nome, textoOuNulo(dados.descricao()),
                dados.preco(), dados.tempoPreparoMin(), dados.destinoPreparo());
        repositorio.save(produto);

        registrar(autor, "PRODUTO_CRIADO", produto, "nome=" + nome + ", preco=" + dados.preco());
        return ProdutoResposta.de(produto);
    }

    @Transactional
    public ProdutoResposta atualizar(Long id, ProdutoRequest dados, FuncionarioDetalhes autor) {
        Produto produto = buscar(id);
        String nome = dados.nome().trim();
        if (repositorio.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new ConflitoException("Já existe um produto chamado " + nome + ".");
        }
        String antes = "preco=" + produto.getPreco();

        produto.setCategoria(buscarCategoria(dados.categoriaId()));
        produto.setNome(nome);
        produto.setDescricao(textoOuNulo(dados.descricao()));
        produto.setPreco(dados.preco());
        produto.setTempoPreparoMin(dados.tempoPreparoMin());
        produto.setDestinoPreparo(dados.destinoPreparo());

        registrar(autor, "PRODUTO_ALTERADO", produto, antes + " -> preco=" + produto.getPreco());
        return ProdutoResposta.de(produto);
    }

    @Transactional
    public ProdutoResposta mudarDisponibilidade(Long id, boolean disponivel, FuncionarioDetalhes autor) {
        Produto produto = buscar(id);
        produto.setDisponivel(disponivel);
        registrar(autor, disponivel ? "PRODUTO_DISPONIVEL" : "PRODUTO_INDISPONIVEL", produto, "nome=" + produto.getNome());
        return ProdutoResposta.de(produto);
    }

    @Transactional
    public ProdutoResposta mudarAtivo(Long id, boolean ativo, FuncionarioDetalhes autor) {
        Produto produto = buscar(id);
        produto.setAtivo(ativo);
        registrar(autor, ativo ? "PRODUTO_RESTAURADO" : "PRODUTO_ARQUIVADO", produto, "nome=" + produto.getNome());
        return ProdutoResposta.de(produto);
    }

    @Transactional
    public ProdutoResposta trocarImagem(Long id, MultipartFile arquivo, FuncionarioDetalhes autor) {
        Produto produto = buscar(id);
        String antiga = produto.getImagem();
        produto.setImagem(imagens.salvar(arquivo));
        imagens.apagar(antiga);

        registrar(autor, "PRODUTO_IMAGEM_ALTERADA", produto, "nome=" + produto.getNome());
        return ProdutoResposta.de(produto);
    }

    @Transactional
    public ProdutoResposta removerImagem(Long id, FuncionarioDetalhes autor) {
        Produto produto = buscar(id);
        imagens.apagar(produto.getImagem());
        produto.setImagem(null);

        registrar(autor, "PRODUTO_IMAGEM_REMOVIDA", produto, "nome=" + produto.getNome());
        return ProdutoResposta.de(produto);
    }

    @Transactional(readOnly = true)
    public ArmazenamentoDeImagens.Imagem lerImagem(Long id) {
        Produto produto = buscar(id);
        if (produto.getImagem() == null) {
            throw new NaoEncontradoException("Este produto não tem imagem.");
        }
        return imagens.ler(produto.getImagem());
    }

    private Produto buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new NaoEncontradoException("Produto não encontrado."));
    }

    private Categoria buscarCategoria(Long id) {
        return categorias.findById(id).orElseThrow(() -> new NaoEncontradoException("Categoria não encontrada."));
    }

    // Descrição em branco vira "sem descrição"
    private String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private void registrar(FuncionarioDetalhes autor, String acao, Produto produto, String detalhes) {
        auditoria.registrar(autor.getId(), autor.getUsername(), acao, "produto", String.valueOf(produto.getId()), detalhes);
    }
}
