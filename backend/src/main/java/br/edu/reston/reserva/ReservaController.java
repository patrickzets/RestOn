package br.edu.reston.reserva;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.reston.atendimento.dto.AtendimentoResposta;
import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.reserva.dto.ReservaRequest;
import br.edu.reston.reserva.dto.ReservaResposta;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reservas")
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GARCOM')")
public class ReservaController {

    private final ReservaService servico;

    public ReservaController(ReservaService servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<ReservaResposta> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            @RequestParam(required = false) StatusReserva status,
            @AuthenticationPrincipal FuncionarioDetalhes quemPede) {
        return servico.listar(de, ate, status, quemPede);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResposta criar(@Valid @RequestBody ReservaRequest dados, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.criar(dados, autor);
    }

    @PutMapping("/{id}")
    public ReservaResposta atualizar(@PathVariable Long id, @Valid @RequestBody ReservaRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.atualizar(id, dados, autor);
    }

    @PostMapping("/{id}/cancelar")
    public ReservaResposta cancelar(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.cancelar(id, autor);
    }

    @PostMapping("/{id}/chegada")
    public AtendimentoResposta chegada(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.registrarChegada(id, autor);
    }
}
