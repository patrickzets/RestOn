package br.edu.reston.auditoria;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "funcionario_id")
    private Long funcionarioId;

    @Column(name = "login_informado", length = 60)
    private String login;

    @Column(nullable = false, length = 60)
    private String acao;

    @Column(length = 60)
    private String entidade;

    @Column(name = "entidade_id", length = 60)
    private String entidadeId;

    @Column(columnDefinition = "text")
    private String detalhes;

    @Column(length = 45)
    private String ip;

    @Column(nullable = false, updatable = false)
    private Instant em;

    protected AuditLog() {
        // exigido pelo JPA
    }

    public AuditLog(Long funcionarioId, String login, String acao, String entidade, String entidadeId, String detalhes, String ip) {
        this.funcionarioId = funcionarioId;
        this.login = login;
        this.acao = acao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.detalhes = detalhes;
        this.ip = ip;
    }

    @PrePersist
    void antesDeInserir() {
        em = Instant.now();
    }

    public Long getId() { return id; }
    public Long getFuncionarioId() { return funcionarioId; }
    public String getLogin() { return login; }
    public String getAcao() { return acao; }
    public String getEntidade() { return entidade; }
    public String getEntidadeId() { return entidadeId; }
    public String getDetalhes() { return detalhes; }
    public String getIp() { return ip; }
    public Instant getEm() { return em; }
}
