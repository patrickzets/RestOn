package br.edu.reston.comanda;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.comanda.dto.ComandaResposta;
import br.edu.reston.comanda.dto.NovaComandaRequest;
import br.edu.reston.pedido.PedidoService;
import br.edu.reston.pedido.dto.NovoPedidoRequest;
import br.edu.reston.pedido.dto.PedidoResposta;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GARCOM')")
public class ComandaController {

    private final ComandaService servico;
    private final PedidoService pedidos;

    public ComandaController(ComandaService servico, PedidoService pedidos) {
        this.servico = servico;
        this.pedidos = pedidos;
    }

    @PostMapping("/atendimentos/{atendimentoId}/comandas")
    @ResponseStatus(HttpStatus.CREATED)
    public ComandaResposta abrir(@PathVariable Long atendimentoId, @Valid @RequestBody(required = false) NovaComandaRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.abrir(atendimentoId, dados == null ? null : dados.nomeCliente(), autor);
    }

    // O caixa também consulta comandas (para receber o pagamento)
    @GetMapping("/comandas")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GARCOM', 'CAIXA')")
    public List<ComandaResposta> listar(@RequestParam(required = false) Long atendimentoId,
            @RequestParam(required = false) Integer mesa, @RequestParam(required = false) String codigo,
            @RequestParam(required = false) StatusComanda status) {
        return servico.listar(atendimentoId, mesa, codigo, status);
    }

    @GetMapping("/comandas/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GARCOM', 'CAIXA')")
    public ComandaResposta detalhe(@PathVariable Long id) {
        return servico.detalhe(id);
    }

    @PostMapping("/comandas/{id}/cancelar")
    public ComandaResposta cancelar(@PathVariable Long id, @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.cancelar(id, autor);
    }

    @PostMapping("/comandas/{id}/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PedidoResposta> lancarPedido(@PathVariable Long id, @Valid @RequestBody NovoPedidoRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return pedidos.criar(id, dados.itens(), autor);
    }
}
