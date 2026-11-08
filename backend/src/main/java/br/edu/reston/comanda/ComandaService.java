package br.edu.reston.comanda;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.reston.atendimento.Atendimento;
import br.edu.reston.atendimento.AtendimentoRepository;
import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.caixa.PagamentoRepository;
import br.edu.reston.caixa.StatusPagamento;
import br.edu.reston.caixa.dto.PagamentoResposta;
import br.edu.reston.comanda.dto.ComandaResposta;
import br.edu.reston.configuracao.ConfiguracaoService;
import br.edu.reston.erro.NaoEncontradoException;
import br.edu.reston.erro.RegraDeNegocioException;
import br.edu.reston.pedido.ItemPedido;
import br.edu.reston.pedido.ItemPedidoRepository;
import br.edu.reston.pedido.PedidoRepository;
import br.edu.reston.pedido.dto.PedidoResposta;
import br.edu.reston.tempo.MesasAlteradas;

@Service
public class ComandaService {

    private static final int LIMITE_DA_LISTA = 200;

    // Os três valores da conta de uma comanda
    public record Totais(BigDecimal subtotal, BigDecimal taxa, BigDecimal total) {
    }

    private final ComandaRepository repositorio;
    private final AtendimentoRepository atendimentos;
    private final PedidoRepository pedidos;
    private final ItemPedidoRepository itens;
    private final PagamentoRepository pagamentos;
    private final ConfiguracaoService configuracao;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;

    public ComandaService(ComandaRepository repositorio, AtendimentoRepository atendimentos, PedidoRepository pedidos,
            ItemPedidoRepository itens, PagamentoRepository pagamentos, ConfiguracaoService configuracao,
            AuditoriaService auditoria, ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.atendimentos = atendimentos;
        this.pedidos = pedidos;
        this.itens = itens;
        this.pagamentos = pagamentos;
        this.configuracao = configuracao;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    @Transactional
    public ComandaResposta abrir(Long atendimentoId, String nomeCliente, FuncionarioDetalhes autor) {
        Atendimento atendimento = atendimentos.findById(atendimentoId)
                .orElseThrow(() -> new NaoEncontradoException("Atendimento não encontrado."));
        if (atendimento.getEncerradoEm() != null) {
            throw new RegraDeNegocioException("Este atendimento já foi encerrado.");
        }

        int numero = repositorio.maiorNumeroDeCliente(atendimentoId) + 1;
        String codigo = String.format("C-%05d", repositorio.proximoNumeroDeCodigo());
        String nome = nomeCliente == null || nomeCliente.isBlank() ? null : nomeCliente.trim();

        Comanda comanda = repositorio.save(new Comanda(codigo, atendimento, numero, nome, autor.getId(), configuracao.taxaDeServico()));

        // O pagamento antecipado da reserva vira crédito na primeira comanda do atendimento
        if (numero == 1 && atendimento.getReserva() != null) {
            pagamentos.findByReservaIdAndComandaIsNullAndStatus(atendimento.getReserva().getId(), StatusPagamento.CONFIRMADO)
                    .forEach(pagamento -> pagamento.setComanda(comanda));
        }

        auditoria.registrar(autor.getId(), autor.getUsername(), "COMANDA_ABERTA", "comanda", String.valueOf(comanda.getId()),
                codigo + ", mesa=" + atendimento.getMesa().getNumero() + ", cliente=" + numero);
        eventos.publishEvent(new MesasAlteradas());
        return resposta(comanda, false);
    }

    // Filtros opcionais: mesa (número), código e situação. Sem nenhum, traz as mais recentes.
    @Transactional(readOnly = true)
    public List<ComandaResposta> listar(Long atendimentoId, Integer mesaNumero, String codigo, StatusComanda status) {
        List<Comanda> lista = atendimentoId != null
                ? repositorio.findByAtendimentoIdOrderByNumeroClienteAsc(atendimentoId)
                : repositorio.findAllByOrderByAbertaEmDesc(PageRequest.of(0, LIMITE_DA_LISTA));

        String busca = codigo == null ? "" : codigo.trim().toLowerCase(Locale.ROOT);
        return lista.stream()
                .filter(c -> status == null || c.getStatus() == status)
                .filter(c -> mesaNumero == null || c.getAtendimento().getMesa().getNumero() == mesaNumero)
                .filter(c -> busca.isEmpty() || c.getCodigo().toLowerCase(Locale.ROOT).contains(busca))
                .map(c -> resposta(c, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public ComandaResposta detalhe(Long id) {
        return resposta(buscar(id), true);
    }

    // Só cancela comanda que ainda não tem nenhum item (comanda aberta por engano)
    @Transactional
    public ComandaResposta cancelar(Long id, FuncionarioDetalhes autor) {
        Comanda comanda = buscar(id);
        if (comanda.getStatus() != StatusComanda.ABERTA) {
            throw new RegraDeNegocioException("Só dá para cancelar uma comanda que está aberta.");
        }
        if (itens.existsByComandaIdAndCanceladoFalse(id)) {
            throw new RegraDeNegocioException("Esta comanda tem itens. Cancele os itens antes de cancelar a comanda.");
        }
        if (pagamentos.somaConfirmadaDaComanda(id).signum() > 0) {
            throw new RegraDeNegocioException("Esta comanda tem pagamento registrado. Estorne o pagamento antes de cancelar.");
        }
        comanda.setStatus(StatusComanda.CANCELADA);

        auditoria.registrar(autor.getId(), autor.getUsername(), "COMANDA_CANCELADA", "comanda", String.valueOf(id), comanda.getCodigo());
        eventos.publishEvent(new MesasAlteradas());
        return resposta(comanda, false);
    }

    // Só Gerente e Administrador (a regra de perfil fica no controller). Ao remover, o motivo é obrigatório.
    @Transactional
    public ComandaResposta alterarTaxa(Long id, boolean removida, String motivo, FuncionarioDetalhes autor) {
        Comanda comanda = buscar(id);
        if (comanda.getStatus() != StatusComanda.ABERTA) {
            throw new RegraDeNegocioException("Só dá para mudar a taxa de uma comanda aberta.");
        }
        if (removida && (motivo == null || motivo.trim().length() < 3)) {
            throw new RegraDeNegocioException("Informe o motivo para remover a taxa de serviço.");
        }

        comanda.setTaxaRemovida(removida);
        // Remover a taxa não pode deixar a comanda com mais pagamento do que o novo total
        Totais novos = totais(comanda);
        if (pagamentos.somaConfirmadaDaComanda(id).compareTo(novos.total()) > 0) {
            comanda.setTaxaRemovida(!removida);
            throw new RegraDeNegocioException("Já foi pago mais do que o novo total. Estorne um pagamento antes de mudar a taxa.");
        }

        auditoria.registrar(autor.getId(), autor.getUsername(), removida ? "TAXA_REMOVIDA" : "TAXA_RESTAURADA", "comanda",
                String.valueOf(id), comanda.getCodigo() + (removida ? ", motivo=" + motivo.trim() : ""));
        eventos.publishEvent(new MesasAlteradas());
        return resposta(comanda, false);
    }

    // Depois de fechada, vale o que foi gravado no fechamento
    public Totais totais(Comanda comanda) {
        if (comanda.getStatus() == StatusComanda.FECHADA && comanda.getTotalFinal() != null) {
            return new Totais(comanda.getSubtotalFinal(), comanda.getTaxaValorFinal(), comanda.getTotalFinal());
        }
        BigDecimal subtotal = itens.findByComandaIdAndCanceladoFalse(comanda.getId()).stream()
                .map(ItemPedido::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taxa = comanda.isTaxaRemovida()
                ? BigDecimal.ZERO
                : subtotal.multiply(comanda.getTaxaPercentual()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Totais(subtotal, taxa, subtotal.add(taxa));
    }

    public ComandaResposta resposta(Comanda comanda, boolean comDetalhes) {
        Totais totais = totais(comanda);
        BigDecimal pago = pagamentos.somaConfirmadaDaComanda(comanda.getId());

        List<PedidoResposta> pedidosDaComanda = comDetalhes ? pedidos.daComanda(comanda.getId()).stream().map(PedidoResposta::de).toList() : null;
        List<PagamentoResposta> pagamentosDaComanda = comDetalhes
                ? pagamentos.findByComandaIdOrderByCriadoEmAsc(comanda.getId()).stream().map(PagamentoResposta::de).toList()
                : null;
        return ComandaResposta.de(comanda, totais.subtotal(), totais.taxa(), pago, pedidosDaComanda, pagamentosDaComanda);
    }

    private Comanda buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new NaoEncontradoException("Comanda não encontrada."));
    }
}
