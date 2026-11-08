package br.edu.reston.reserva;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.reston.atendimento.AtendimentoService;
import br.edu.reston.atendimento.dto.AtendimentoResposta;
import br.edu.reston.caixa.PagamentoRepository;
import br.edu.reston.caixa.PagamentoService;
import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.configuracao.ConfiguracaoService;
import br.edu.reston.erro.ConflitoException;
import br.edu.reston.erro.NaoEncontradoException;
import br.edu.reston.erro.RegraDeNegocioException;
import br.edu.reston.funcionario.Perfil;
import br.edu.reston.mesa.Mesa;
import br.edu.reston.mesa.MesaRepository;
import br.edu.reston.mesa.MesaService;
import br.edu.reston.reserva.dto.ReservaRequest;
import br.edu.reston.reserva.dto.ReservaResposta;
import br.edu.reston.tempo.MesasAlteradas;

@Service
public class ReservaService {

    private final ReservaRepository repositorio;
    private final MesaRepository mesas;
    private final AtendimentoService atendimentos;
    private final ConfiguracaoService configuracao;
    private final PagamentoRepository pagamentos;
    private final PagamentoService pagamentoService;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;

    public ReservaService(ReservaRepository repositorio, MesaRepository mesas, AtendimentoService atendimentos,
            ConfiguracaoService configuracao, PagamentoRepository pagamentos, PagamentoService pagamentoService,
            AuditoriaService auditoria, ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.mesas = mesas;
        this.atendimentos = atendimentos;
        this.configuracao = configuracao;
        this.pagamentos = pagamentos;
        this.pagamentoService = pagamentoService;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public List<ReservaResposta> listar(Instant de, Instant ate, StatusReserva status, FuncionarioDetalhes quemPede) {
        return repositorio.doPeriodo(de, ate).stream()
                .filter(r -> status == null || r.getStatus() == status)
                .map(r -> resposta(r, quemPede))
                .toList();
    }

    @Transactional
    public ReservaResposta criar(ReservaRequest dados, FuncionarioDetalhes autor) {
        Mesa mesa = buscarMesa(dados.mesaId());
        Instant fim = dados.inicio().plus(configuracao.duracaoDaReserva(), ChronoUnit.MINUTES);
        if (dados.telefone() == null) {
            throw new RegraDeNegocioException("Informe o telefone do cliente.");
        }
        validar(dados, mesa, fim, -1L);

        Reserva reserva = new Reserva(mesa, dados.nomeCliente().trim(), soNumeros(dados.telefone()),
                dados.qtdPessoas(), dados.inicio(), fim, autor.getId());
        salvar(reserva);

        registrar(autor, "RESERVA_CRIADA", reserva, descrever(reserva));
        return resposta(reserva, autor);
    }

    @Transactional
    public ReservaResposta atualizar(Long id, ReservaRequest dados, FuncionarioDetalhes autor) {
        Reserva reserva = buscar(id);
        if (reserva.getStatus() != StatusReserva.ATIVA) {
            throw new RegraDeNegocioException("Só dá para alterar uma reserva que ainda está ativa.");
        }
        Mesa mesa = buscarMesa(dados.mesaId());
        Instant fim = dados.inicio().plus(configuracao.duracaoDaReserva(), ChronoUnit.MINUTES);
        validar(dados, mesa, fim, id);

        reserva.setMesa(mesa);
        reserva.setNomeCliente(dados.nomeCliente().trim());
        if (dados.telefone() != null) {
            reserva.setTelefone(soNumeros(dados.telefone()));
        }
        reserva.setQtdPessoas(dados.qtdPessoas());
        reserva.setInicio(dados.inicio());
        reserva.setFim(fim);
        salvar(reserva);

        registrar(autor, "RESERVA_ALTERADA", reserva, descrever(reserva));
        return resposta(reserva, autor);
    }

    @Transactional
    public ReservaResposta cancelar(Long id, FuncionarioDetalhes autor) {
        Reserva reserva = buscar(id);
        if (reserva.getStatus() != StatusReserva.ATIVA) {
            throw new RegraDeNegocioException("Só dá para cancelar uma reserva que ainda está ativa.");
        }
        // O que foi pago antecipado volta para o cliente (estorno simulado)
        pagamentoService.estornarAntecipadosDaReserva(reserva, autor);
        reserva.setStatus(StatusReserva.CANCELADA);

        registrar(autor, "RESERVA_CANCELADA", reserva, descrever(reserva));
        return resposta(reserva, autor);
    }

    // O cliente chegou: a reserva vira atendimento na mesa reservada
    @Transactional
    public AtendimentoResposta registrarChegada(Long id, FuncionarioDetalhes autor) {
        Reserva reserva = buscar(id);
        if (reserva.getStatus() != StatusReserva.ATIVA) {
            throw new RegraDeNegocioException("Só dá para registrar a chegada de uma reserva ativa.");
        }
        Instant agora = Instant.now();
        if (agora.isBefore(reserva.getInicio().minus(MesaService.ANTECEDENCIA_DA_RESERVA_MIN, ChronoUnit.MINUTES))) {
            throw new RegraDeNegocioException("Ainda é cedo para esta reserva. A chegada só pode ser registrada até 1 hora antes do horário.");
        }
        if (agora.isAfter(reserva.getFim())) {
            throw new RegraDeNegocioException("O horário desta reserva já passou.");
        }

        var atendimento = atendimentos.abrirParaReserva(reserva, autor);
        reserva.setStatus(StatusReserva.CHEGOU);

        registrar(autor, "RESERVA_CHEGOU", reserva, "mesa=" + reserva.getMesa().getNumero());
        return AtendimentoResposta.de(atendimento);
    }

    private void validar(ReservaRequest dados, Mesa mesa, Instant fim, Long ignorarId) {
        if (dados.inicio().isBefore(Instant.now().minus(5, ChronoUnit.MINUTES))) {
            throw new RegraDeNegocioException("O horário da reserva precisa estar no futuro.");
        }
        if (!mesa.isAtiva()) {
            throw new RegraDeNegocioException("A mesa " + mesa.getNumero() + " está desativada.");
        }
        if (dados.qtdPessoas() > mesa.getCapacidade()) {
            throw new RegraDeNegocioException("A mesa " + mesa.getNumero() + " comporta no máximo " + mesa.getCapacidade() + " pessoas.");
        }
        if (repositorio.existeConflito(mesa.getId(), StatusReserva.QUE_SEGURAM_A_MESA, dados.inicio(), fim, ignorarId)) {
            throw new ConflitoException("A mesa " + mesa.getNumero() + " já tem uma reserva neste horário.");
        }
    }

    // Se duas pessoas reservarem ao mesmo tempo, o banco recusa a segunda
    private void salvar(Reserva reserva) {
        try {
            repositorio.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException erro) {
            throw new ConflitoException("A mesa " + reserva.getMesa().getNumero() + " já tem uma reserva neste horário.");
        }
    }

    private ReservaResposta resposta(Reserva reserva, FuncionarioDetalhes quemPede) {
        boolean podeVerTelefone = quemPede.getPerfil() != Perfil.GARCOM;
        Instant limite = reserva.getInicio().plus(configuracao.toleranciaDaReserva(), ChronoUnit.MINUTES);
        boolean atrasada = reserva.getStatus() == StatusReserva.ATIVA && Instant.now().isAfter(limite);
        return ReservaResposta.de(reserva, podeVerTelefone, atrasada, pagamentos.somaAntecipadaDaReserva(reserva.getId()));
    }

    private Reserva buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new NaoEncontradoException("Reserva não encontrada."));
    }

    private Mesa buscarMesa(Long id) {
        return mesas.findById(id).orElseThrow(() -> new NaoEncontradoException("Mesa não encontrada."));
    }

    private String soNumeros(String telefone) {
        return telefone.replaceAll("\\D", "");
    }

    private String descrever(Reserva r) {
        return "mesa=" + r.getMesa().getNumero() + ", pessoas=" + r.getQtdPessoas() + ", inicio=" + r.getInicio();
    }

    private void registrar(FuncionarioDetalhes autor, String acao, Reserva reserva, String detalhes) {
        auditoria.registrar(autor.getId(), autor.getUsername(), acao, "reserva", String.valueOf(reserva.getId()), detalhes);
        // As reservas aparecem no painel de mesas
        eventos.publishEvent(new MesasAlteradas());
    }
}
