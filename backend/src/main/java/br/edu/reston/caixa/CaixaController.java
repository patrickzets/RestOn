package br.edu.reston.caixa;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.caixa.dto.AbrirTurnoRequest;
import br.edu.reston.caixa.dto.AnteciparRequest;
import br.edu.reston.caixa.dto.ComprovanteResposta;
import br.edu.reston.caixa.dto.FecharTurnoRequest;
import br.edu.reston.caixa.dto.MotivoRequest;
import br.edu.reston.caixa.dto.PagamentoRequest;
import br.edu.reston.caixa.dto.PagamentoResposta;
import br.edu.reston.caixa.dto.TaxaRequest;
import br.edu.reston.caixa.dto.TurnoResposta;
import br.edu.reston.comanda.ComandaService;
import br.edu.reston.comanda.dto.ComandaResposta;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class CaixaController {

    private final CaixaService turnos;
    private final PagamentoService pagamentos;
    private final ComandaService comandas;

    public CaixaController(CaixaService turnos, PagamentoService pagamentos, ComandaService comandas) {
        this.turnos = turnos;
        this.pagamentos = pagamentos;
        this.comandas = comandas;
    }

    @PostMapping("/caixa/turnos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'CAIXA')")
    public TurnoResposta abrirTurno(@Valid @RequestBody AbrirTurnoRequest dados, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return turnos.abrir(dados.valorInicial(), autor);
    }

    // Sem caixa aberto a resposta é 204 (sem conteúdo)
    @GetMapping("/caixa/turno-atual")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'CAIXA')")
    public ResponseEntity<TurnoResposta> turnoAtual() {
        return turnos.turnoAtual().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/caixa/turnos")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<TurnoResposta> historico() {
        return turnos.historico();
    }

    @PostMapping("/caixa/turnos/{id}/fechar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'CAIXA')")
    public TurnoResposta fecharTurno(@PathVariable Long id, @Valid @RequestBody FecharTurnoRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return turnos.fechar(id, dados.valorContado(), autor);
    }

    @PostMapping("/comandas/{id}/pagamentos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'CAIXA')")
    public PagamentoResposta pagar(@PathVariable Long id, @Valid @RequestBody PagamentoRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return pagamentos.pagar(id, dados, autor);
    }

    @PostMapping("/comandas/{id}/fechar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'CAIXA')")
    public ComandaResposta fecharComanda(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return pagamentos.fechar(id, autor);
    }

    // O garçom também pode imprimir a conta parcial para o cliente conferir
    @GetMapping("/comandas/{id}/comprovante")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'CAIXA', 'GARCOM')")
    public ComprovanteResposta comprovante(@PathVariable Long id) {
        return pagamentos.comprovante(id);
    }

    @PatchMapping("/comandas/{id}/taxa")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ComandaResposta alterarTaxa(@PathVariable Long id, @Valid @RequestBody TaxaRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return comandas.alterarTaxa(id, dados.removida(), dados.motivo(), autor);
    }

    @PostMapping("/pagamentos/{id}/estornar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public PagamentoResposta estornar(@PathVariable Long id, @Valid @RequestBody MotivoRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return pagamentos.estornar(id, dados.motivo(), autor);
    }

    @PostMapping("/reservas/{id}/pagamento-antecipado")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public PagamentoResposta anteciparPagamento(@PathVariable Long id, @Valid @RequestBody AnteciparRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return pagamentos.antecipar(id, dados.metodo(), dados.valor(), autor);
    }
}
