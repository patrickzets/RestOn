package br.edu.reston.pedido;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.cardapio.DestinoPreparo;
import br.edu.reston.cardapio.Produto;
import br.edu.reston.cardapio.ProdutoRepository;
import br.edu.reston.comanda.Comanda;
import br.edu.reston.comanda.ComandaRepository;
import br.edu.reston.comanda.StatusComanda;
import br.edu.reston.erro.AcessoNegadoException;
import br.edu.reston.erro.NaoEncontradoException;
import br.edu.reston.erro.RegraDeNegocioException;
import br.edu.reston.estoque.EstoqueService;
import br.edu.reston.funcionario.Perfil;
import br.edu.reston.pedido.dto.ItemRequest;
import br.edu.reston.pedido.dto.ItemResposta;
import br.edu.reston.pedido.dto.PedidoResposta;
import br.edu.reston.tempo.MesasAlteradas;
import br.edu.reston.tempo.PedidosAlterados;

@Service
public class PedidoService {

    private static final Set<StatusPedido> EM_ANDAMENTO = EnumSet.of(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARO, StatusPedido.PRONTO);

    private final PedidoRepository repositorio;
    private final ItemPedidoRepository itens;
    private final PedidoHistoricoRepository historico;
    private final ComandaRepository comandas;
    private final ProdutoRepository produtos;
    private final EstoqueService estoque;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;

    public PedidoService(PedidoRepository repositorio, ItemPedidoRepository itens, PedidoHistoricoRepository historico,
            ComandaRepository comandas, ProdutoRepository produtos, EstoqueService estoque, AuditoriaService auditoria,
            ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.itens = itens;
        this.historico = historico;
        this.comandas = comandas;
        this.produtos = produtos;
        this.estoque = estoque;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    // Pedidos que ainda precisam da cozinha ou do bar (ou esperam o garçom entregar)
    @Transactional(readOnly = true)
    public List<PedidoResposta> emAndamento(DestinoPreparo destino, Perfil perfil) {
        // O bartender só enxerga o bar, mesmo que peça outro destino
        DestinoPreparo filtro = perfil == Perfil.BARTENDER ? DestinoPreparo.BAR : destino;
        return repositorio.comStatus(EM_ANDAMENTO).stream()
                .filter(p -> filtro == null || p.getDestino() == filtro)
                .map(PedidoResposta::de)
                .toList();
    }

    // Um envio com itens de destinos diferentes vira um pedido por destino
    @Transactional
    public List<PedidoResposta> criar(Long comandaId, List<ItemRequest> pedidos, FuncionarioDetalhes autor) {
        Comanda comanda = buscarComanda(comandaId);
        if (comanda.getStatus() != StatusComanda.ABERTA) {
            throw new RegraDeNegocioException("A comanda " + comanda.getCodigo() + " não está aberta.");
        }

        // Trava os produtos antes de ler o saldo, para dois pedidos juntos não venderem a mesma unidade
        produtos.travar(pedidos.stream().map(ItemRequest::produtoId).distinct().sorted().toList());

        Map<DestinoPreparo, Pedido> porDestino = new LinkedHashMap<>();
        for (ItemRequest pedido : pedidos) {
            Produto produto = produtos.findById(pedido.produtoId())
                    .orElseThrow(() -> new NaoEncontradoException("Produto não encontrado."));
            if (!produto.isAtivo() || !produto.isDisponivel()) {
                throw new RegraDeNegocioException(produto.getNome() + " não está disponível no momento.");
            }

            // Produto sem preparo não passa pela cozinha: já nasce pronto para entregar
            StatusPedido inicial = produto.getDestinoPreparo() == DestinoPreparo.SEM_PREPARO ? StatusPedido.PRONTO : StatusPedido.RECEBIDO;
            Pedido pedidoDoDestino = porDestino.computeIfAbsent(produto.getDestinoPreparo(),
                    destino -> new Pedido(comanda, destino, inicial, autor.getId()));
            pedidoDoDestino.adicionar(new ItemPedido(pedidoDoDestino, comanda, produto, pedido.quantidade(), textoOuNulo(pedido.observacao())));
        }

        List<PedidoResposta> criados = porDestino.values().stream().map(p -> {
            repositorio.save(p);
            for (ItemPedido item : p.getItens()) {
                estoque.baixar(item.getProduto(), item.getQuantidade(), "Pedido #" + p.getId() + " (" + comanda.getCodigo() + ")", autor.getId());
            }
            historico.save(new PedidoHistorico(p.getId(), null, p.getStatus(), autor.getId()));
            auditoria.registrar(autor.getId(), autor.getUsername(), "PEDIDO_CRIADO", "pedido", String.valueOf(p.getId()),
                    comanda.getCodigo() + ", destino=" + p.getDestino() + ", itens=" + p.getItens().size());
            return PedidoResposta.de(p);
        }).toList();

        avisarAsTelas();
        return criados;
    }

    // Cada passo do pedido: Recebido -> Pronto -> Entregue (o Em preparo ainda existe, mas a tela da cozinha não usa)
    @Transactional
    public PedidoResposta mudarStatus(Long id, StatusPedido novo, FuncionarioDetalhes autor) {
        Pedido pedido = buscar(id);
        StatusPedido atual = pedido.getStatus();

        // A cozinha não precisa avisar que começou: do Recebido ela pode ir direto para o Pronto
        boolean direto = atual == StatusPedido.RECEBIDO && novo == StatusPedido.PRONTO;
        if (novo != atual.proximo() && !direto) {
            throw new RegraDeNegocioException("Este pedido está " + descrever(atual) + " e não pode ir para " + descrever(novo) + ".");
        }
        // A cozinha prepara; o garçom entrega. Gerente e administrador fazem os dois.
        // O bartender também prepara, mas só os pedidos do bar.
        boolean entrega = novo == StatusPedido.ENTREGUE;
        Perfil perfil = autor.getPerfil();
        boolean gestao = perfil == Perfil.ADMIN || perfil == Perfil.GERENTE;
        boolean prepara = perfil == Perfil.COZINHA || perfil == Perfil.BARTENDER;
        if (!gestao && ((entrega && perfil != Perfil.GARCOM) || (!entrega && !prepara))) {
            throw new AcessoNegadoException(entrega ? "Só o garçom confirma a entrega." : "Só a cozinha ou o bar muda o preparo do pedido.");
        }
        if (perfil == Perfil.BARTENDER && pedido.getDestino() != DestinoPreparo.BAR) {
            throw new AcessoNegadoException("O bartender só mexe nos pedidos do bar.");
        }

        mudar(pedido, novo, autor);
        auditoria.registrar(autor.getId(), autor.getUsername(), "PEDIDO_STATUS", "pedido", String.valueOf(id), atual + " -> " + novo);
        avisarAsTelas();
        return PedidoResposta.de(pedido);
    }

    @Transactional
    public PedidoResposta cancelarItem(Long itemId, String motivo, FuncionarioDetalhes autor) {
        ItemPedido item = buscarItem(itemId);
        Pedido pedido = item.getPedido();

        if (item.isCancelado()) {
            throw new RegraDeNegocioException("Este item já foi cancelado.");
        }
        if (item.getComanda().getStatus() != StatusComanda.ABERTA) {
            throw new RegraDeNegocioException("A comanda deste item não está aberta.");
        }
        if (pedido.getStatus() == StatusPedido.ENTREGUE) {
            throw new RegraDeNegocioException("Este item já foi entregue e não pode ser cancelado.");
        }
        // Depois que a cozinha começou, só um gestor decide (o preparo já teve custo)
        boolean jaEmPreparo = pedido.getStatus() == StatusPedido.EM_PREPARO || pedido.getStatus() == StatusPedido.PRONTO;
        if (jaEmPreparo && autor.getPerfil() == Perfil.GARCOM) {
            throw new AcessoNegadoException("Só o Gerente ou o Administrador cancela um item que já está em preparo ou pronto.");
        }

        // O que já foi preparado não volta para a prateleira; bebida e itens sem preparo voltam
        boolean volta = pedido.getStatus() == StatusPedido.RECEBIDO || item.getProduto().getDestinoPreparo() == DestinoPreparo.SEM_PREPARO;
        if (volta) {
            produtos.travar(List.of(item.getProduto().getId()));
            estoque.devolver(item.getProduto(), item.getQuantidade(), "Item cancelado no pedido #" + pedido.getId(), autor.getId());
        }

        item.setCancelado(true);
        item.setMotivoCancelamento(motivo.trim());
        if (pedido.getItens().stream().allMatch(ItemPedido::isCancelado)) {
            mudarParaCancelado(pedido, autor);
        }

        auditoria.registrar(autor.getId(), autor.getUsername(), "ITEM_CANCELADO", "item_pedido", String.valueOf(itemId),
                item.getProduto().getNome() + " x" + item.getQuantidade() + ", motivo=" + motivo.trim());
        avisarAsTelas();
        return PedidoResposta.de(pedido);
    }

    // Passa a cobrança do item para outra comanda do mesmo atendimento (por exemplo, para dividir a conta)
    @Transactional
    public ItemResposta transferirItem(Long itemId, Long comandaDestinoId, FuncionarioDetalhes autor) {
        ItemPedido item = buscarItem(itemId);
        Comanda origem = item.getComanda();
        Comanda destino = buscarComanda(comandaDestinoId);

        if (item.isCancelado() || item.getPedido().getStatus() == StatusPedido.CANCELADO) {
            throw new RegraDeNegocioException("Um item cancelado não pode ser transferido.");
        }
        if (origem.getId().equals(destino.getId())) {
            throw new RegraDeNegocioException("O item já está nesta comanda.");
        }
        if (!origem.getAtendimento().getId().equals(destino.getAtendimento().getId())) {
            throw new RegraDeNegocioException("Só dá para transferir entre comandas da mesma mesa.");
        }
        if (origem.getStatus() != StatusComanda.ABERTA || destino.getStatus() != StatusComanda.ABERTA) {
            throw new RegraDeNegocioException("As duas comandas precisam estar abertas.");
        }

        item.setComanda(destino);
        auditoria.registrar(autor.getId(), autor.getUsername(), "ITEM_TRANSFERIDO", "item_pedido", String.valueOf(itemId),
                item.getProduto().getNome() + ": " + origem.getCodigo() + " -> " + destino.getCodigo());
        eventos.publishEvent(new MesasAlteradas());
        return ItemResposta.de(item);
    }

    private void mudar(Pedido pedido, StatusPedido novo, FuncionarioDetalhes autor) {
        historico.save(new PedidoHistorico(pedido.getId(), pedido.getStatus(), novo, autor.getId()));
        pedido.setStatus(novo);
    }

    private void mudarParaCancelado(Pedido pedido, FuncionarioDetalhes autor) {
        mudar(pedido, StatusPedido.CANCELADO, autor);
    }

    private void avisarAsTelas() {
        eventos.publishEvent(new PedidosAlterados());
        eventos.publishEvent(new MesasAlteradas());
    }

    private String descrever(StatusPedido status) {
        return switch (status) {
            case RECEBIDO -> "recebido";
            case EM_PREPARO -> "em preparo";
            case PRONTO -> "pronto";
            case ENTREGUE -> "entregue";
            case CANCELADO -> "cancelado";
        };
    }

    private String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private Pedido buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new NaoEncontradoException("Pedido não encontrado."));
    }

    private ItemPedido buscarItem(Long id) {
        return itens.findById(id).orElseThrow(() -> new NaoEncontradoException("Item não encontrado."));
    }

    private Comanda buscarComanda(Long id) {
        return comandas.findById(id).orElseThrow(() -> new NaoEncontradoException("Comanda não encontrada."));
    }
}
