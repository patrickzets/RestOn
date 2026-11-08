package br.edu.reston.atendimento;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.reston.atendimento.dto.AtendimentoResposta;
import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.comanda.ComandaRepository;
import br.edu.reston.comanda.StatusComanda;
import br.edu.reston.erro.NaoEncontradoException;
import br.edu.reston.erro.RegraDeNegocioException;
import br.edu.reston.mesa.EstadoMesa;
import br.edu.reston.mesa.Mesa;
import br.edu.reston.mesa.MesaRepository;
import br.edu.reston.mesa.MesaService;
import br.edu.reston.reserva.Reserva;
import br.edu.reston.reserva.ReservaRepository;
import br.edu.reston.reserva.StatusReserva;
import br.edu.reston.tempo.MesasAlteradas;

@Service
public class AtendimentoService {

    private final AtendimentoRepository repositorio;
    private final MesaRepository mesas;
    private final ReservaRepository reservas;
    private final ComandaRepository comandas;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;

    public AtendimentoService(AtendimentoRepository repositorio, MesaRepository mesas, ReservaRepository reservas,
            ComandaRepository comandas, AuditoriaService auditoria, ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.mesas = mesas;
        this.reservas = reservas;
        this.comandas = comandas;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    // Cliente que chegou sem reserva
    @Transactional
    public AtendimentoResposta abrir(Long mesaId, FuncionarioDetalhes autor) {
        Mesa mesa = buscarMesa(mesaId);
        return AtendimentoResposta.de(abrirNaMesa(mesa, null, autor));
    }

    // Cliente que chegou com reserva (chamado pelo serviço de reservas)
    @Transactional
    public Atendimento abrirParaReserva(Reserva reserva, FuncionarioDetalhes autor) {
        return abrirNaMesa(reserva.getMesa(), reserva, autor);
    }

    @Transactional
    public AtendimentoResposta trocarMesa(Long atendimentoId, Long novaMesaId, FuncionarioDetalhes autor) {
        Atendimento atendimento = buscarAberto(atendimentoId);
        Mesa antiga = atendimento.getMesa();
        Mesa nova = buscarMesa(novaMesaId);

        if (antiga.getId().equals(nova.getId())) {
            throw new RegraDeNegocioException("O atendimento já está nesta mesa.");
        }
        garantirQuePodeSentar(nova, -1L);

        // A mesa de origem ainda precisa ser arrumada antes de voltar a ficar livre
        antiga.setEstado(EstadoMesa.AGUARDANDO_LIBERACAO);
        nova.setEstado(EstadoMesa.OCUPADA);
        atendimento.setMesa(nova);

        auditoria.registrar(autor.getId(), autor.getUsername(), "MESA_TROCADA", "atendimento",
                String.valueOf(atendimento.getId()), "mesa " + antiga.getNumero() + " -> mesa " + nova.getNumero());
        eventos.publishEvent(new MesasAlteradas());
        return AtendimentoResposta.de(atendimento);
    }

    // A mesa só passa para aguardando liberação quando não sobra nenhuma comanda aberta
    @Transactional
    public AtendimentoResposta encerrar(Long atendimentoId, FuncionarioDetalhes autor) {
        Atendimento atendimento = buscarAberto(atendimentoId);
        if (comandas.existsByAtendimentoIdAndStatus(atendimentoId, StatusComanda.ABERTA)) {
            throw new RegraDeNegocioException("Ainda há comandas abertas nesta mesa. Feche ou cancele todas antes de encerrar.");
        }

        atendimento.setEncerradoEm(Instant.now());
        atendimento.getMesa().setEstado(EstadoMesa.AGUARDANDO_LIBERACAO);
        if (atendimento.getReserva() != null) {
            atendimento.getReserva().setStatus(StatusReserva.CONCLUIDA);
        }

        auditoria.registrar(autor.getId(), autor.getUsername(), "ATENDIMENTO_ENCERRADO", "atendimento",
                String.valueOf(atendimento.getId()), "mesa=" + atendimento.getMesa().getNumero());
        eventos.publishEvent(new MesasAlteradas());
        return AtendimentoResposta.de(atendimento);
    }

    private Atendimento abrirNaMesa(Mesa mesa, Reserva reserva, FuncionarioDetalhes autor) {
        garantirQuePodeSentar(mesa, reserva == null ? -1L : reserva.getId());

        mesa.setEstado(EstadoMesa.OCUPADA);
        Atendimento atendimento = repositorio.save(new Atendimento(mesa, reserva, autor.getId()));

        String detalhes = "mesa=" + mesa.getNumero() + (reserva == null ? "" : ", reserva=" + reserva.getId());
        auditoria.registrar(autor.getId(), autor.getUsername(), "ATENDIMENTO_ABERTO", "atendimento",
                String.valueOf(atendimento.getId()), detalhes);
        eventos.publishEvent(new MesasAlteradas());
        return atendimento;
    }

    // A mesa precisa estar ativa, livre e sem outra reserva chegando logo
    private void garantirQuePodeSentar(Mesa mesa, Long reservaPermitidaId) {
        if (!mesa.isAtiva()) {
            throw new RegraDeNegocioException("A mesa " + mesa.getNumero() + " está desativada.");
        }
        if (mesa.getEstado() != EstadoMesa.LIVRE) {
            throw new RegraDeNegocioException("A mesa " + mesa.getNumero() + " não está livre.");
        }
        Instant agora = Instant.now();
        Instant limite = agora.plus(MesaService.ANTECEDENCIA_DA_RESERVA_MIN, ChronoUnit.MINUTES);
        if (reservas.existeConflito(mesa.getId(), List.of(StatusReserva.ATIVA), agora, limite, reservaPermitidaId)) {
            throw new RegraDeNegocioException("A mesa " + mesa.getNumero() + " tem uma reserva chegando. Escolha outra mesa.");
        }
    }

    private Mesa buscarMesa(Long id) {
        return mesas.findById(id).orElseThrow(() -> new NaoEncontradoException("Mesa não encontrada."));
    }

    private Atendimento buscarAberto(Long id) {
        Atendimento atendimento = repositorio.findById(id)
                .orElseThrow(() -> new NaoEncontradoException("Atendimento não encontrado."));
        if (atendimento.getEncerradoEm() != null) {
            throw new RegraDeNegocioException("Este atendimento já foi encerrado.");
        }
        return atendimento;
    }
}
