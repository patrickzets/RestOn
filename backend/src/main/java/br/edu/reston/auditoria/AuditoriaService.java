package br.edu.reston.auditoria;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditoriaService {

    private static final int TAMANHO_MAXIMO = 100;

    private final AuditLogRepository repositorio;

    public AuditoriaService(AuditLogRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public void registrar(Long funcionarioId, String login, String acao, String entidade, String entidadeId, String detalhes) {
        repositorio.save(new AuditLog(funcionarioId, login, acao, entidade, entidadeId, detalhes, ipAtual()));
    }

    @Transactional(readOnly = true)
    public PaginaResposta<AuditoriaResposta> listar(int pagina, int tamanho, String acao) {
        int tamanhoSeguro = Math.max(1, Math.min(tamanho, TAMANHO_MAXIMO));
        var resultado = repositorio.findByAcaoContainingIgnoreCaseOrderByEmDesc(
                acao == null ? "" : acao.trim(), PageRequest.of(Math.max(0, pagina), tamanhoSeguro));

        return new PaginaResposta<>(
                resultado.getContent().stream().map(AuditoriaResposta::de).toList(),
                resultado.getTotalElements(),
                resultado.getNumber(),
                resultado.getSize());
    }

    private String ipAtual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            return atributos.getRequest().getRemoteAddr();
        }
        return null;
    }
}
