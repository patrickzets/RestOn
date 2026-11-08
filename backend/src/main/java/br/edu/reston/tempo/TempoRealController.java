package br.edu.reston.tempo;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/stream")
public class TempoRealController {

    private final TempoRealService servico;

    public TempoRealController(TempoRealService servico) {
        this.servico = servico;
    }

    @GetMapping(path = "/pedidos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GARCOM', 'COZINHA', 'BARTENDER', 'CAIXA')")
    public SseEmitter pedidos() {
        return servico.inscrever(TempoRealService.PEDIDOS);
    }

    @GetMapping(path = "/mesas", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GARCOM', 'CAIXA')")
    public SseEmitter mesas() {
        return servico.inscrever(TempoRealService.MESAS);
    }
}
