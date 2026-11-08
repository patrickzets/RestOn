package br.edu.reston.cardapio;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.cardapio.dto.AtivoRequest;
import br.edu.reston.cardapio.dto.DisponibilidadeRequest;
import br.edu.reston.cardapio.dto.ProdutoRequest;
import br.edu.reston.cardapio.dto.ProdutoResposta;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/produtos")
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GARCOM', 'CAIXA')")
public class ProdutoController {

    private final ProdutoService servico;

    public ProdutoController(ProdutoService servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<ProdutoResposta> listar(@AuthenticationPrincipal FuncionarioDetalhes quemPede) {
        return servico.listar(quemPede);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ProdutoResposta criar(@Valid @RequestBody ProdutoRequest dados, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.criar(dados, autor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ProdutoResposta atualizar(@PathVariable Long id, @Valid @RequestBody ProdutoRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.atualizar(id, dados, autor);
    }

    @PatchMapping("/{id}/disponibilidade")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ProdutoResposta disponibilidade(@PathVariable Long id, @Valid @RequestBody DisponibilidadeRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.mudarDisponibilidade(id, dados.disponivel(), autor);
    }

    @PatchMapping("/{id}/ativo")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ProdutoResposta ativo(@PathVariable Long id, @Valid @RequestBody AtivoRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.mudarAtivo(id, dados.ativo(), autor);
    }

    @PostMapping("/{id}/imagem")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ProdutoResposta trocarImagem(@PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.trocarImagem(id, arquivo, autor);
    }

    @DeleteMapping("/{id}/imagem")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ProdutoResposta removerImagem(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.removerImagem(id, autor);
    }

    // A tela pede a foto com ?v=<nome do arquivo>, então o navegador pode guardá-la por bastante tempo
    @GetMapping("/{id}/imagem")
    public ResponseEntity<byte[]> imagem(@PathVariable Long id) {
        ArmazenamentoDeImagens.Imagem imagem = servico.lerImagem(id);
        return ResponseEntity.ok()
                .contentType(imagem.tipo())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(imagem.bytes());
    }
}
