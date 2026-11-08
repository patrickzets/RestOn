package br.edu.reston.mesa;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.reston.atendimento.Atendimento;
import br.edu.reston.atendimento.AtendimentoRepository;
import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.comanda.ComandaRepository;
import br.edu.reston.configuracao.ConfiguracaoService;
import br.edu.reston.erro.ConflitoException;
import br.edu.reston.erro.NaoEncontradoException;
import br.edu.reston.erro.RegraDeNegocioException;
import br.edu.reston.mesa.dto.MesaRequest;
import br.edu.reston.mesa.dto.MesaResposta;
import br.edu.reston.pedido.PedidoRepository;
import br.edu.reston.reserva.Reserva;
import br.edu.reston.reserva.ReservaRepository;
import br.edu.reston.reserva.StatusReserva;
import br.edu.reston.tempo.MesasAlteradas;

@Service
public class MesaService {

    // Quanto tempo antes da reserva a mesa passa a aparecer como "reservada" e não aceita outro atendimento
    public static final int ANTECEDENCIA_DA_RESERVA_MIN = 60;

    private final MesaRepository repositorio;
    private final AtendimentoRepository atendimentos;
    private final ReservaRepository reservas;
    private final ConfiguracaoService configuracao;
    private final ComandaRepository comandas;
    private final PedidoRepository pedidos;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;

    public MesaService(MesaRepository repositorio, AtendimentoRepository atendimentos, ReservaRepository reservas,
            ConfiguracaoService configuracao, ComandaRepository comandas, PedidoRepository pedidos, AuditoriaService auditoria,
            ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.atendimentos = atendimentos;
        this.reservas = reservas;
        this.configuracao = configuracao;
        this.comandas = comandas;
        this.pedidos = pedidos;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public List<MesaResposta> listar() {
        Instant agora = Instant.now();

        Map<Long, Atendimento> abertos = new HashMap<>();
        for (Atendimento a : atendimentos.findByEncerradoEmIsNull()) {
            abertos.put(a.getMesa().getId(), a);
        }

        // A lista vem da reserva mais cedo para a mais tarde, então guardamos só a primeira de cada mesa
        Map<Long, Reserva> proximas = new HashMap<>();
        for (Reserva r : reservas.ativasAte(agora, agora.plus(ANTECEDENCIA_DA_RESERVA_MIN, ChronoUnit.MINUTES))) {
            proximas.putIfAbsent(r.getMesa().getId(), r);
        }

        Map<Long, Long> comandasAbertas = contar(comandas.contarAbertasPorMesa());
        Map<Long, Long> pedidosProntos = contar(pedidos.contarProntosPorMesa());

        return repositorio.findAllByOrderByNumeroAsc().stream()
                .map(m -> MesaResposta.de(m, abertos.get(m.getId()), proximas.get(m.getId()),
                        comandasAbertas.getOrDefault(m.getId(), 0L).intValue(), pedidosProntos.getOrDefault(m.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional
    public MesaResposta criar(MesaRequest dados, FuncionarioDetalhes autor) {
        if (repositorio.existsByNumero(dados.numero())) {
            throw new ConflitoException("Já existe a mesa " + dados.numero() + ".");
        }
        Mesa mesa = new Mesa(dados.numero(), dados.capacidade());
        mesa.setAtiva(dados.ativa());
        mesa.setSetor(dados.setorOuPadrao());
        repositorio.save(mesa);

        registrar(autor, "MESA_CRIADA", mesa, "numero=" + mesa.getNumero() + ", capacidade=" + mesa.getCapacidade());
        eventos.publishEvent(new MesasAlteradas());
        return MesaResposta.de(mesa, null, null);
    }

    @Transactional
    public MesaResposta atualizar(Long id, MesaRequest dados, FuncionarioDetalhes autor) {
        Mesa mesa = buscar(id);
        if (repositorio.existsByNumeroAndIdNot(dados.numero(), id)) {
            throw new ConflitoException("Já existe a mesa " + dados.numero() + ".");
        }
        if (mesa.isAtiva() && !dados.ativa()) {
            if (mesa.getEstado() != EstadoMesa.LIVRE) {
                throw new RegraDeNegocioException("Só dá para desativar uma mesa que está livre.");
            }
            if (reservas.existsByMesaIdAndStatusAndFimAfter(id, StatusReserva.ATIVA, Instant.now())) {
                throw new RegraDeNegocioException("Esta mesa tem reservas pendentes. Cancele ou mude as reservas antes de desativar.");
            }
        }

        mesa.setNumero(dados.numero());
        mesa.setCapacidade(dados.capacidade());
        mesa.setAtiva(dados.ativa());
        mesa.setSetor(dados.setorOuPadrao());

        registrar(autor, "MESA_ALTERADA", mesa,
                "numero=" + mesa.getNumero() + ", capacidade=" + mesa.getCapacidade() + ", ativa=" + mesa.isAtiva());
        eventos.publishEvent(new MesasAlteradas());
        return MesaResposta.de(mesa, null, null);
    }

    // Confirma que a mesa foi limpa e arrumada: só depois disso ela volta a ficar livre
    @Transactional
    public MesaResposta liberar(Long id, FuncionarioDetalhes autor) {
        Mesa mesa = buscar(id);
        if (mesa.getEstado() != EstadoMesa.AGUARDANDO_LIBERACAO) {
            throw new RegraDeNegocioException("A mesa " + mesa.getNumero() + " não está aguardando liberação.");
        }
        mesa.setEstado(EstadoMesa.LIVRE);

        registrar(autor, "MESA_LIBERADA", mesa, "numero=" + mesa.getNumero());
        eventos.publishEvent(new MesasAlteradas());
        return MesaResposta.de(mesa, null, null);
    }

    // Mesas que comportam o grupo e não têm reserva cruzando com o horário
    @Transactional(readOnly = true)
    public List<MesaResposta> disponiveis(Instant inicio, int pessoas, Long ignorarReservaId) {
        Instant fim = inicio.plus(configuracao.duracaoDaReserva(), ChronoUnit.MINUTES);
        List<Long> ocupadas = reservas.mesasOcupadasNoHorario(StatusReserva.QUE_SEGURAM_A_MESA, inicio, fim,
                ignorarReservaId == null ? -1L : ignorarReservaId);

        return repositorio.findAllByOrderByNumeroAsc().stream()
                .filter(m -> m.isAtiva() && m.getCapacidade() >= pessoas && !ocupadas.contains(m.getId()))
                .map(m -> MesaResposta.de(m, null, null))
                .toList();
    }

    // Transforma as linhas [id da mesa, quantidade] de uma consulta num mapa
    private Map<Long, Long> contar(List<Object[]> linhas) {
        Map<Long, Long> contagem = new HashMap<>();
        for (Object[] linha : linhas) {
            contagem.put((Long) linha[0], (Long) linha[1]);
        }
        return contagem;
    }

    private Mesa buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new NaoEncontradoException("Mesa não encontrada."));
    }

    private void registrar(FuncionarioDetalhes autor, String acao, Mesa mesa, String detalhes) {
        auditoria.registrar(autor.getId(), autor.getUsername(), acao, "mesa", String.valueOf(mesa.getId()), detalhes);
    }
}
