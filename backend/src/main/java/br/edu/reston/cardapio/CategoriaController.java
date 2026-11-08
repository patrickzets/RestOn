package br.edu.reston.cardapio;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.cardapio.dto.CategoriaRequest;
import br.edu.reston.cardapio.dto.CategoriaResposta;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/categorias")
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GARCOM', 'CAIXA')")
public class CategoriaController {

    private final CategoriaService servico;

    public CategoriaController(CategoriaService servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<CategoriaResposta> listar() {
        return servico.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public CategoriaResposta criar(@Valid @RequestBody CategoriaRequest dados, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.criar(dados, autor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public CategoriaResposta renomear(@PathVariable Long id, @Valid @RequestBody CategoriaRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.renomear(id, dados, autor);
    }

    @PostMapping("/{id}/subir")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<CategoriaResposta> subir(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.mover(id, -1, autor);
    }

    @PostMapping("/{id}/descer")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<CategoriaResposta> descer(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.mover(id, 1, autor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public void excluir(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        servico.excluir(id, autor);
    }
}
