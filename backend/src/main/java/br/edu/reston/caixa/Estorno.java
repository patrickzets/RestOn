package br.edu.reston.caixa;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// O estorno é um registro novo: o pagamento original continua lá, só muda de status
@Entity
@Table(name = "estorno")
public class Estorno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pagamento_id", nullable = false, updatable = false)
    private Long pagamentoId;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal valor;

    @Column(nullable = false, length = 120, updatable = false)
    private String motivo;

    @Column(name = "autorizado_por", nullable = false, updatable = false)
    private Long autorizadoPor;

    @Column(nullable = false, updatable = false)
    private Instant em = Instant.now();

    protected Estorno() {
    }

    public Estorno(Long pagamentoId, BigDecimal valor, String motivo, Long autorizadoPor) {
        this.pagamentoId = pagamentoId;
        this.valor = valor;
        this.motivo = motivo;
        this.autorizadoPor = autorizadoPor;
    }

    public Long getId() { return id; }
    public Long getPagamentoId() { return pagamentoId; }
    public BigDecimal getValor() { return valor; }
    public String getMotivo() { return motivo; }
    public Instant getEm() { return em; }
}
