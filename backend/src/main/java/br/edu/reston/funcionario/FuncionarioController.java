package br.edu.reston.funcionario;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.funcionario.dto.AtualizarFuncionarioRequest;
import br.edu.reston.funcionario.dto.FuncionarioCriadoResposta;
import br.edu.reston.funcionario.dto.FuncionarioResposta;
import br.edu.reston.funcionario.dto.NovoFuncionarioRequest;
import br.edu.reston.funcionario.dto.SenhaProvisoriaResposta;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/funcionarios")
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
public class FuncionarioController {

    private final FuncionarioService servico;

    public FuncionarioController(FuncionarioService servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<FuncionarioResposta> listar() {
        return servico.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FuncionarioCriadoResposta criar(@Valid @RequestBody NovoFuncionarioRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.criar(dados, autor);
    }

    @PutMapping("/{id}")
    public FuncionarioResposta atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarFuncionarioRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.atualizar(id, dados, autor);
    }

    @PatchMapping("/{id}/bloquear")
    public FuncionarioResposta bloquear(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.bloquear(id, autor);
    }

    @PatchMapping("/{id}/desbloquear")
    public FuncionarioResposta desbloquear(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.desbloquear(id, autor);
    }

    @PatchMapping("/{id}/desativar")
    public FuncionarioResposta desativar(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.desativar(id, autor);
    }

    @PatchMapping("/{id}/ativar")
    public FuncionarioResposta ativar(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.ativar(id, autor);
    }

    @PostMapping("/{id}/redefinir-senha")
    public SenhaProvisoriaResposta redefinirSenha(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.redefinirSenha(id, autor);
    }
}
