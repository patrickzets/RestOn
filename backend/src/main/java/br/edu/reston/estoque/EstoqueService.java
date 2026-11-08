package br.edu.reston.estoque;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.cardapio.Produto;
import br.edu.reston.cardapio.ProdutoRepository;
import br.edu.reston.erro.NaoEncontradoException;
import br.edu.reston.erro.RegraDeNegocioException;
import br.edu.reston.estoque.dto.AjusteRequest;
import br.edu.reston.estoque.dto.ControleRequest;
import br.edu.reston.estoque.dto.EntradaRequest;
import br.edu.reston.estoque.dto.EstoqueResposta;
import br.edu.reston.estoque.dto.MovimentacaoResposta;
import br.edu.reston.funcionario.Funcionario;
import br.edu.reston.funcionario.FuncionarioRepository;

@Service
public class EstoqueService {

    private final ProdutoRepository produtos;
    private final MovimentacaoRepository movimentacoes;
    private final FuncionarioRepository funcionarios;
    private final AuditoriaService auditoria;

    public EstoqueService(ProdutoRepository produtos, MovimentacaoRepository movimentacoes, FuncionarioRepository funcionarios,
            AuditoriaService auditoria) {
        this.produtos = produtos;
        this.movimentacoes = movimentacoes;
        this.funcionarios = funcionarios;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<EstoqueResposta> listar() {
        return produtos.findAllByAtivoTrueOrderByNomeAsc().stream().map(EstoqueResposta::de).toList();
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoResposta> historico(Long produtoId) {
        buscar(produtoId);
        List<MovimentacaoEstoque> lista = movimentacoes.findTop50ByProdutoIdOrderByCriadoEmDescIdDesc(produtoId);
        Map<Long, String> nomes = funcionarios.findAllById(lista.stream().map(MovimentacaoEstoque::getFuncionarioId)
                .filter(id -> id != null).distinct().toList())
                .stream().collect(Collectors.toMap(Funcionario::getId, Funcionario::getNome));
        return lista.stream().map(m -> MovimentacaoResposta.de(m, nomes.get(m.getFuncionarioId()))).toList();
    }

    // Liga ou desliga o controle e define o mínimo. Ao ligar, a quantidade inicial vira o primeiro saldo.
    @Transactional
    public EstoqueResposta configurar(Long produtoId, ControleRequest dados, FuncionarioDetalhes autor) {
        Produto produto = travar(produtoId);
        boolean ligando = dados.controla() && !produto.isControlaEstoque();

        produto.setControlaEstoque(dados.controla());
        produto.setEstoqueMinimo(dados.minimo());
        if (ligando) {
            int inicial = dados.quantidadeInicial() == null ? 0 : dados.quantidadeInicial();
            produto.setEstoqueAtual(inicial);
            if (inicial > 0) {
                movimentacoes.save(new MovimentacaoEstoque(produtoId, TipoMovimentacao.AJUSTE, inicial, inicial,
                        "Início do controle de estoque", autor.getId()));
            }
        }
        auditoria.registrar(autor.getId(), autor.getUsername(), "ESTOQUE_CONTROLE", "produto", String.valueOf(produtoId),
                produto.getNome() + ": controle=" + dados.controla() + ", minimo=" + dados.minimo());
        return EstoqueResposta.de(produto);
    }

    @Transactional
    public EstoqueResposta registrarEntrada(Long produtoId, EntradaRequest dados, FuncionarioDetalhes autor) {
        Produto produto = travarControlado(produtoId);
        int novo = produto.getEstoqueAtual() + dados.quantidade();
        produto.setEstoqueAtual(novo);
        String observacao = dados.observacao() == null || dados.observacao().isBlank() ? "Entrada de mercadoria" : dados.observacao().trim();
        movimentacoes.save(new MovimentacaoEstoque(produtoId, TipoMovimentacao.ENTRADA, dados.quantidade(), novo, observacao, autor.getId()));
        auditoria.registrar(autor.getId(), autor.getUsername(), "ESTOQUE_ENTRADA", "produto", String.valueOf(produtoId),
                produto.getNome() + " +" + dados.quantidade() + " (saldo " + novo + ")");
        return EstoqueResposta.de(produto);
    }

    // Contagem física: o saldo passa a ser o número informado
    @Transactional
    public EstoqueResposta ajustar(Long produtoId, AjusteRequest dados, FuncionarioDetalhes autor) {
        Produto produto = travarControlado(produtoId);
        int diferenca = dados.quantidade() - produto.getEstoqueAtual();
        if (diferenca == 0) {
            throw new RegraDeNegocioException("A quantidade informada é igual ao saldo atual.");
        }
        produto.setEstoqueAtual(dados.quantidade());
        movimentacoes.save(new MovimentacaoEstoque(produtoId, TipoMovimentacao.AJUSTE, diferenca, dados.quantidade(),
                dados.motivo().trim(), autor.getId()));
        auditoria.registrar(autor.getId(), autor.getUsername(), "ESTOQUE_AJUSTE", "produto", String.valueOf(produtoId),
                produto.getNome() + ": " + (dados.quantidade() - diferenca) + " -> " + dados.quantidade() + ", motivo=" + dados.motivo().trim());
        return EstoqueResposta.de(produto);
    }

    // Chamado pelo pedido: o produto precisa estar travado (ver ProdutoRepository.travar)
    public void baixar(Produto produto, int quantidade, String motivo, Long funcionarioId) {
        if (!produto.isControlaEstoque()) {
            return;
        }
        if (produto.getEstoqueAtual() < quantidade) {
            String restam = produto.getEstoqueAtual() == 0 ? "esgotado" : "restam só " + produto.getEstoqueAtual();
            throw new RegraDeNegocioException("Estoque insuficiente de " + produto.getNome() + ": " + restam + ".");
        }
        int novo = produto.getEstoqueAtual() - quantidade;
        produto.setEstoqueAtual(novo);
        movimentacoes.save(new MovimentacaoEstoque(produto.getId(), TipoMovimentacao.SAIDA_PEDIDO, -quantidade, novo, motivo, funcionarioId));
    }

    public void devolver(Produto produto, int quantidade, String motivo, Long funcionarioId) {
        if (!produto.isControlaEstoque()) {
            return;
        }
        int novo = produto.getEstoqueAtual() + quantidade;
        produto.setEstoqueAtual(novo);
        movimentacoes.save(new MovimentacaoEstoque(produto.getId(), TipoMovimentacao.DEVOLUCAO, quantidade, novo, motivo, funcionarioId));
    }

    private Produto buscar(Long id) {
        return produtos.findById(id).orElseThrow(() -> new NaoEncontradoException("Produto não encontrado."));
    }

    private Produto travar(Long id) {
        return produtos.travar(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new NaoEncontradoException("Produto não encontrado."));
    }

    private Produto travarControlado(Long id) {
        Produto produto = travar(id);
        if (!produto.isControlaEstoque()) {
            throw new RegraDeNegocioException(produto.getNome() + " não tem controle de estoque. Ative o controle primeiro.");
        }
        return produto;
    }
}
