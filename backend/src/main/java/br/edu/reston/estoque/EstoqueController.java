package br.edu.reston.estoque;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.estoque.dto.AjusteRequest;
import br.edu.reston.estoque.dto.ControleRequest;
import br.edu.reston.estoque.dto.EntradaRequest;
import br.edu.reston.estoque.dto.EstoqueResposta;
import br.edu.reston.estoque.dto.MovimentacaoResposta;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/estoque")
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
public class EstoqueController {

    private final EstoqueService servico;

    public EstoqueController(EstoqueService servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<EstoqueResposta> listar() {
        return servico.listar();
    }

    @GetMapping("/{produtoId}/movimentacoes")
    public List<MovimentacaoResposta> historico(@PathVariable Long produtoId) {
        return servico.historico(produtoId);
    }

    @PutMapping("/{produtoId}/controle")
    public EstoqueResposta configurar(@PathVariable Long produtoId, @Valid @RequestBody ControleRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.configurar(produtoId, dados, autor);
    }

    @PostMapping("/{produtoId}/entradas")
    public EstoqueResposta entrada(@PathVariable Long produtoId, @Valid @RequestBody EntradaRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.registrarEntrada(produtoId, dados, autor);
    }

    @PostMapping("/{produtoId}/ajustes")
    public EstoqueResposta ajuste(@PathVariable Long produtoId, @Valid @RequestBody AjusteRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.ajustar(produtoId, dados, autor);
    }
}
