package br.edu.reston.caixa;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.caixa.dto.TurnoResposta;
import br.edu.reston.erro.AcessoNegadoException;
import br.edu.reston.erro.ConflitoException;
import br.edu.reston.erro.NaoEncontradoException;
import br.edu.reston.erro.RegraDeNegocioException;
import br.edu.reston.funcionario.FuncionarioRepository;
import br.edu.reston.funcionario.Perfil;

@Service
public class CaixaService {

    private final TurnoRepository repositorio;
    private final PagamentoRepository pagamentos;
    private final FuncionarioRepository funcionarios;
    private final AuditoriaService auditoria;

    public CaixaService(TurnoRepository repositorio, PagamentoRepository pagamentos, FuncionarioRepository funcionarios,
            AuditoriaService auditoria) {
        this.repositorio = repositorio;
        this.pagamentos = pagamentos;
        this.funcionarios = funcionarios;
        this.auditoria = auditoria;
    }

    // Só existe um caixa aberto por vez (o banco também garante isso)
    @Transactional
    public TurnoResposta abrir(BigDecimal valorInicial, FuncionarioDetalhes autor) {
        if (repositorio.findByStatus(StatusTurno.ABERTO).isPresent()) {
            throw new ConflitoException("Já existe um caixa aberto. Feche o turno atual antes de abrir outro.");
        }
        CaixaTurno turno;
        try {
            turno = repositorio.saveAndFlush(new CaixaTurno(autor.getId(), valorInicial));
        } catch (DataIntegrityViolationException erro) {
            throw new ConflitoException("Já existe um caixa aberto. Feche o turno atual antes de abrir outro.");
        }

        auditoria.registrar(autor.getId(), autor.getUsername(), "TURNO_ABERTO", "caixa_turno", String.valueOf(turno.getId()),
                "valorInicial=" + valorInicial);
        return resposta(turno);
    }

    @Transactional(readOnly = true)
    public Optional<TurnoResposta> turnoAtual() {
        return repositorio.findByStatus(StatusTurno.ABERTO).map(this::resposta);
    }

    @Transactional(readOnly = true)
    public List<TurnoResposta> historico() {
        return repositorio.findTop30ByOrderByAbertoEmDesc().stream().map(this::resposta).toList();
    }

    @Transactional
    public TurnoResposta fechar(Long id, BigDecimal valorContado, FuncionarioDetalhes autor) {
        CaixaTurno turno = repositorio.findById(id).orElseThrow(() -> new NaoEncontradoException("Turno não encontrado."));
        if (turno.getStatus() != StatusTurno.ABERTO) {
            throw new RegraDeNegocioException("Este turno já foi fechado.");
        }
        // O operador fecha o próprio caixa; gerente e administrador podem fechar o de qualquer pessoa
        if (autor.getPerfil() == Perfil.CAIXA && !turno.getOperadorId().equals(autor.getId())) {
            throw new AcessoNegadoException("Só quem abriu o caixa (ou um gerente) pode fechá-lo.");
        }

        turno.fechar(valorContado);
        TurnoResposta resposta = resposta(turno);

        auditoria.registrar(autor.getId(), autor.getUsername(), "TURNO_FECHADO", "caixa_turno", String.valueOf(id),
                "esperado=" + resposta.dinheiroEsperado() + ", contado=" + valorContado + ", diferenca=" + resposta.diferenca());
        return resposta;
    }

    private TurnoResposta resposta(CaixaTurno turno) {
        BigDecimal pix = BigDecimal.ZERO;
        BigDecimal cartao = BigDecimal.ZERO;
        BigDecimal dinheiro = BigDecimal.ZERO;
        long quantidade = 0;
        for (Object[] linha : pagamentos.totaisDoTurno(turno.getId())) {
            BigDecimal soma = (BigDecimal) linha[1];
            quantidade += (Long) linha[2];
            switch ((MetodoPagamento) linha[0]) {
                case PIX -> pix = soma;
                case CARTAO -> cartao = soma;
                case DINHEIRO -> dinheiro = soma;
            }
        }

        BigDecimal esperado = turno.getValorInicial().add(dinheiro);
        BigDecimal diferenca = turno.getValorContado() == null ? null : turno.getValorContado().subtract(esperado);
        String operador = funcionarios.findById(turno.getOperadorId()).map(f -> f.getNome()).orElse("—");

        return new TurnoResposta(turno.getId(), turno.getOperadorId(), operador, turno.getAbertoEm(), turno.getFechadoEm(),
                turno.getValorInicial(), turno.getValorContado(), turno.getStatus(), pix, cartao, dinheiro,
                pix.add(cartao).add(dinheiro), quantidade, esperado, diferenca);
    }
}
