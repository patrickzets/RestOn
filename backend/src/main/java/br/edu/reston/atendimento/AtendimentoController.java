package br.edu.reston.atendimento;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.reston.atendimento.dto.AtendimentoResposta;
import br.edu.reston.atendimento.dto.TrocarMesaRequest;
import br.edu.reston.auth.FuncionarioDetalhes;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GARCOM')")
public class AtendimentoController {

    private final AtendimentoService servico;

    public AtendimentoController(AtendimentoService servico) {
        this.servico = servico;
    }

    @PostMapping("/mesas/{mesaId}/atendimentos")
    @ResponseStatus(HttpStatus.CREATED)
    public AtendimentoResposta abrir(@PathVariable Long mesaId, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.abrir(mesaId, autor);
    }

    @PostMapping("/atendimentos/{id}/trocar-mesa")
    public AtendimentoResposta trocarMesa(@PathVariable Long id, @Valid @RequestBody TrocarMesaRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.trocarMesa(id, dados.novaMesaId(), autor);
    }

    @PostMapping("/atendimentos/{id}/encerrar")
    public AtendimentoResposta encerrar(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.encerrar(id, autor);
    }
}
