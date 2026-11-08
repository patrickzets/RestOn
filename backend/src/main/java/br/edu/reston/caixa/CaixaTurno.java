package br.edu.reston.caixa;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "caixa_turno")
public class CaixaTurno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operador_id", nullable = false, updatable = false)
    private Long operadorId;

    @Column(name = "aberto_em", nullable = false, updatable = false)
    private Instant abertoEm = Instant.now();

    @Column(name = "fechado_em")
    private Instant fechadoEm;

    @Column(name = "valor_inicial", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorInicial;

    @Column(name = "valor_contado", precision = 12, scale = 2)
    private BigDecimal valorContado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusTurno status = StatusTurno.ABERTO;

    @Version
    private Long version;

    protected CaixaTurno() {
    }

    public CaixaTurno(Long operadorId, BigDecimal valorInicial) {
        this.operadorId = operadorId;
        this.valorInicial = valorInicial;
    }

    public void fechar(BigDecimal contado) {
        this.valorContado = contado;
        this.fechadoEm = Instant.now();
        this.status = StatusTurno.FECHADO;
    }

    public Long getId() { return id; }
    public Long getOperadorId() { return operadorId; }
    public Instant getAbertoEm() { return abertoEm; }
    public Instant getFechadoEm() { return fechadoEm; }
    public BigDecimal getValorInicial() { return valorInicial; }
    public BigDecimal getValorContado() { return valorContado; }
    public StatusTurno getStatus() { return status; }
}
