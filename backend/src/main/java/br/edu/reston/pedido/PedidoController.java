package br.edu.reston.pedido;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.cardapio.DestinoPreparo;
import br.edu.reston.pedido.dto.CancelarItemRequest;
import br.edu.reston.pedido.dto.ItemResposta;
import br.edu.reston.pedido.dto.PedidoResposta;
import br.edu.reston.pedido.dto.StatusPedidoRequest;
import br.edu.reston.pedido.dto.TransferirItemRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GARCOM', 'COZINHA', 'BARTENDER')")
public class PedidoController {

    private final PedidoService servico;

    public PedidoController(PedidoService servico) {
        this.servico = servico;
    }

    @GetMapping("/cozinha/pedidos")
    public List<PedidoResposta> emAndamento(@RequestParam(required = false) DestinoPreparo destino,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.emAndamento(destino, autor.getPerfil());
    }

    @PatchMapping("/pedidos/{id}/status")
    public PedidoResposta mudarStatus(@PathVariable Long id, @Valid @RequestBody StatusPedidoRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.mudarStatus(id, dados.status(), autor);
    }

    @PostMapping("/itens/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GARCOM')")
    public PedidoResposta cancelarItem(@PathVariable Long id, @Valid @RequestBody CancelarItemRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.cancelarItem(id, dados.motivo(), autor);
    }

    @PostMapping("/itens/{id}/transferir")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GARCOM')")
    public ItemResposta transferirItem(@PathVariable Long id, @Valid @RequestBody TransferirItemRequest dados,
            @AuthenticationPrincipal FuncionarioDetalhes autor) {
        return servico.transferirItem(id, dados.comandaDestinoId(), autor);
    }
}
