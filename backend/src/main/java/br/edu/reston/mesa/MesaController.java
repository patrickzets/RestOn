package br.edu.reston.mesa;

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

import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.mesa.dto.MesaRequest;
import br.edu.reston.mesa.dto.MesaResposta;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/mesas")
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GARCOM')")
public class MesaController {

    private final MesaService servico;

    public MesaController(MesaService servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<MesaResposta> listar() {
        return servico.listar();
    }

    @GetMapping("/disponibilidade")
    public List<MesaResposta> disponiveis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant inicio,
            @RequestParam int pessoas,
            @RequestParam(required = false) Long ignorarReserva) {
        return servico.disponiveis(inicio, pessoas, ignorarReserva);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public MesaResposta criar(@Valid @RequestBody MesaRequest dados, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.criar(dados, autor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public MesaResposta atualizar(@PathVariable Long id, @Valid @RequestBody MesaRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.atualizar(id, dados, autor);
    }

    @PostMapping("/{id}/liberar")
    public MesaResposta liberar(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.liberar(id, autor);
    }
}
