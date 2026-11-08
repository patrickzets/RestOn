package br.edu.reston.caixa;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.reston.atendimento.AtendimentoService;
import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.caixa.dto.ComprovanteResposta;
import br.edu.reston.caixa.dto.PagamentoRequest;
import br.edu.reston.caixa.dto.PagamentoResposta;
import br.edu.reston.comanda.Comanda;
import br.edu.reston.comanda.ComandaRepository;
import br.edu.reston.comanda.ComandaService;
import br.edu.reston.comanda.StatusComanda;
import br.edu.reston.comanda.dto.ComandaResposta;
import br.edu.reston.erro.NaoEncontradoException;
import br.edu.reston.erro.RegraDeNegocioException;
import br.edu.reston.pedido.ItemPedidoRepository;
import br.edu.reston.pedido.StatusPedido;
import br.edu.reston.reserva.Reserva;
import br.edu.reston.reserva.ReservaRepository;
import br.edu.reston.reserva.StatusReserva;
import br.edu.reston.tempo.MesasAlteradas;

// Os pagamentos são simulados: o sistema só registra o que o caixa informa (não fala com banco nem maquininha)
@Service
public class PagamentoService {

    private static final NumberFormat REAIS = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

    private final PagamentoRepository repositorio;
    private final EstornoRepository estornos;
    private final TurnoRepository turnos;
    private final ComandaRepository comandas;
    private final ComandaService comandaService;
    private final ItemPedidoRepository itens;
    private final ReservaRepository reservas;
    private final AtendimentoService atendimentos;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;

    public PagamentoService(PagamentoRepository repositorio, EstornoRepository estornos, TurnoRepository turnos,
            ComandaRepository comandas, ComandaService comandaService, ItemPedidoRepository itens, ReservaRepository reservas,
            AtendimentoService atendimentos, AuditoriaService auditoria, ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.estornos = estornos;
        this.turnos = turnos;
        this.comandas = comandas;
        this.comandaService = comandaService;
        this.itens = itens;
        this.reservas = reservas;
        this.atendimentos = atendimentos;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    // Recebe um pagamento (a conta pode ser dividida em vários). Sem caixa aberto, não recebe.
    @Transactional
    public PagamentoResposta pagar(Long comandaId, PagamentoRequest dados, FuncionarioDetalhes autor) {
        Comanda comanda = buscarComanda(comandaId);
        if (comanda.getStatus() != StatusComanda.ABERTA) {
            throw new RegraDeNegocioException("A comanda " + comanda.getCodigo() + " não está aberta.");
        }
        CaixaTurno turno = turnos.findByStatus(StatusTurno.ABERTO)
                .orElseThrow(() -> new RegraDeNegocioException("Abra o caixa antes de receber pagamentos."));

        BigDecimal total = comandaService.totais(comanda).total();
        BigDecimal restante = total.subtract(repositorio.somaConfirmadaDaComanda(comandaId));
        if (restante.signum() <= 0) {
            throw new RegraDeNegocioException("Esta comanda já está totalmente paga.");
        }
        if (dados.valor().compareTo(restante) > 0) {
            throw new RegraDeNegocioException("O valor é maior do que falta pagar (" + reais(restante) + ").");
        }

        // Em dinheiro o cliente pode entregar mais que o valor e receber troco; nas outras formas o valor é exato
        BigDecimal recebido = dados.valor();
        BigDecimal troco = BigDecimal.ZERO;
        if (dados.metodo() == MetodoPagamento.DINHEIRO) {
            recebido = dados.valorRecebido() == null ? dados.valor() : dados.valorRecebido();
            if (recebido.compareTo(dados.valor()) < 0) {
                throw new RegraDeNegocioException("O valor recebido é menor que o valor a pagar.");
            }
            troco = recebido.subtract(dados.valor());
        } else if (dados.valorRecebido() != null && dados.valorRecebido().compareTo(dados.valor()) != 0) {
            throw new RegraDeNegocioException("Troco só existe em dinheiro.");
        }

        Pagamento pagamento = repositorio.save(new Pagamento(comanda, null, turno, dados.metodo(), OrigemPagamento.CAIXA,
                dados.valor(), recebido, troco, autor.getId()));

        auditoria.registrar(autor.getId(), autor.getUsername(), "PAGAMENTO_RECEBIDO", "pagamento", String.valueOf(pagamento.getId()),
                comanda.getCodigo() + ", " + dados.metodo() + ", valor=" + dados.valor() + (troco.signum() > 0 ? ", troco=" + troco : ""));
        return PagamentoResposta.de(pagamento);
    }

    // Só fecha com tudo pago e tudo entregue. Ao fechar a última comanda, a mesa vai para aguardando liberação.
    @Transactional
    public ComandaResposta fechar(Long comandaId, FuncionarioDetalhes autor) {
        Comanda comanda = buscarComanda(comandaId);
        if (comanda.getStatus() != StatusComanda.ABERTA) {
            throw new RegraDeNegocioException("A comanda " + comanda.getCodigo() + " não está aberta.");
        }

        ComandaService.Totais totais = comandaService.totais(comanda);
        if (totais.total().signum() == 0) {
            throw new RegraDeNegocioException("Esta comanda não tem nada a cobrar. Se foi aberta por engano, cancele-a.");
        }
        BigDecimal falta = totais.total().subtract(repositorio.somaConfirmadaDaComanda(comandaId));
        if (falta.signum() > 0) {
            throw new RegraDeNegocioException("Ainda faltam " + reais(falta) + " para fechar a comanda.");
        }
        // Pedido pronto já conta como entregue: só o que ainda está na cozinha ou no bar segura o fechamento
        List<StatusPedido> naoEntregues = List.of(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARO);
        if (itens.existsByComandaIdAndCanceladoFalseAndPedidoStatusIn(comandaId, naoEntregues)) {
            throw new RegraDeNegocioException("Ainda há pedidos desta comanda que não ficaram prontos.");
        }

        comanda.fechar(totais.subtotal(), totais.taxa(), totais.total());
        auditoria.registrar(autor.getId(), autor.getUsername(), "COMANDA_FECHADA", "comanda", String.valueOf(comandaId),
                comanda.getCodigo() + ", total=" + totais.total());

        Long atendimentoId = comanda.getAtendimento().getId();
        if (!comandas.existsByAtendimentoIdAndStatus(atendimentoId, StatusComanda.ABERTA)) {
            atendimentos.encerrar(atendimentoId, autor);
        }
        eventos.publishEvent(new MesasAlteradas());
        return comandaService.resposta(comanda, true);
    }

    // O estorno não apaga nada: o pagamento fica no histórico e ganha um registro de estorno com o motivo
    @Transactional
    public PagamentoResposta estornar(Long pagamentoId, String motivo, FuncionarioDetalhes autor) {
        Pagamento pagamento = repositorio.findById(pagamentoId)
                .orElseThrow(() -> new NaoEncontradoException("Pagamento não encontrado."));
        if (pagamento.getStatus() != StatusPagamento.CONFIRMADO) {
            throw new RegraDeNegocioException("Este pagamento já foi estornado.");
        }

        registrarEstorno(pagamento, motivo.trim(), autor);
        auditoria.registrar(autor.getId(), autor.getUsername(), "PAGAMENTO_ESTORNADO", "pagamento", String.valueOf(pagamentoId),
                pagamento.getMetodo() + ", valor=" + pagamento.getValor() + ", motivo=" + motivo.trim());
        eventos.publishEvent(new MesasAlteradas());
        return PagamentoResposta.de(pagamento);
    }

    // Pagamento antecipado de uma reserva (só PIX ou cartão): vira crédito na primeira comanda quando o cliente chegar
    @Transactional
    public PagamentoResposta antecipar(Long reservaId, MetodoPagamento metodo, BigDecimal valor, FuncionarioDetalhes autor) {
        Reserva reserva = reservas.findById(reservaId).orElseThrow(() -> new NaoEncontradoException("Reserva não encontrada."));
        if (reserva.getStatus() != StatusReserva.ATIVA) {
            throw new RegraDeNegocioException("Só dá para receber antecipado de uma reserva que ainda está ativa.");
        }
        if (metodo == MetodoPagamento.DINHEIRO) {
            throw new RegraDeNegocioException("O pagamento antecipado só pode ser por PIX ou cartão.");
        }
        if (repositorio.somaAntecipadaDaReserva(reservaId).signum() > 0) {
            throw new RegraDeNegocioException("Esta reserva já tem um pagamento antecipado.");
        }

        Pagamento pagamento = repositorio.save(new Pagamento(null, reservaId, null, metodo, OrigemPagamento.RESERVA, valor, valor,
                BigDecimal.ZERO, autor.getId()));

        auditoria.registrar(autor.getId(), autor.getUsername(), "PAGAMENTO_ANTECIPADO", "reserva", String.valueOf(reservaId),
                metodo + ", valor=" + valor);
        eventos.publishEvent(new MesasAlteradas());
        return PagamentoResposta.de(pagamento);
    }

    // Cancelar a reserva devolve o que foi pago antecipado (estorno simulado)
    @Transactional
    public void estornarAntecipadosDaReserva(Reserva reserva, FuncionarioDetalhes autor) {
        for (Pagamento pagamento : repositorio.findByReservaIdAndOrigemAndStatus(reserva.getId(), OrigemPagamento.RESERVA, StatusPagamento.CONFIRMADO)) {
            registrarEstorno(pagamento, "Reserva cancelada", autor);
            auditoria.registrar(autor.getId(), autor.getUsername(), "PAGAMENTO_ESTORNADO", "pagamento", String.valueOf(pagamento.getId()),
                    "reserva cancelada, valor=" + pagamento.getValor());
        }
    }

    @Transactional(readOnly = true)
    public ComprovanteResposta comprovante(Long comandaId) {
        Comanda comanda = buscarComanda(comandaId);
        if (comanda.getStatus() == StatusComanda.CANCELADA) {
            throw new RegraDeNegocioException("Uma comanda cancelada não tem comprovante.");
        }

        ComandaService.Totais totais = comandaService.totais(comanda);
        List<ComprovanteResposta.Linha> linhas = itens.findByComandaIdAndCanceladoFalse(comandaId).stream()
                .map(i -> new ComprovanteResposta.Linha(i.getProduto().getNome(), i.getQuantidade(), i.getPrecoUnitario(),
                        i.subtotal(), i.getObservacao()))
                .toList();
        List<Pagamento> confirmados = repositorio.findByComandaIdOrderByCriadoEmAsc(comandaId).stream()
                .filter(p -> p.getStatus() == StatusPagamento.CONFIRMADO)
                .toList();

        BigDecimal pago = confirmados.stream().map(Pagamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal troco = confirmados.stream().map(Pagamento::getTroco).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ComprovanteResposta(comanda.getCodigo(), comanda.getStatus(), comanda.getAtendimento().getMesa().getNumero(),
                comanda.getNumeroCliente(), comanda.getNomeCliente(), comanda.getAbertaEm(), comanda.getFechadaEm(), linhas,
                totais.subtotal(), comanda.getTaxaPercentual(), comanda.isTaxaRemovida(), totais.taxa(), totais.total(),
                confirmados.stream().map(PagamentoResposta::de).toList(), pago, troco);
    }

    private void registrarEstorno(Pagamento pagamento, String motivo, FuncionarioDetalhes autor) {
        estornos.save(new Estorno(pagamento.getId(), pagamento.getValor(), motivo, autor.getId()));
        pagamento.setStatus(StatusPagamento.ESTORNADO);
    }

    private Comanda buscarComanda(Long id) {
        return comandas.findById(id).orElseThrow(() -> new NaoEncontradoException("Comanda não encontrada."));
    }

    private String reais(BigDecimal valor) {
        return REAIS.format(valor);
    }
}
