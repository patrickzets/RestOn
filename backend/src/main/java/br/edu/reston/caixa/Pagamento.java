package br.edu.reston.caixa;

import java.math.BigDecimal;
import java.time.Instant;

import br.edu.reston.comanda.Comanda;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "pagamento")
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Vazia enquanto o pagamento é antecipado de uma reserva e a comanda ainda não existe
    @ManyToOne
    private Comanda comanda;

    @Column(name = "reserva_id")
    private Long reservaId;

    // Pagamento antecipado de reserva não passa por turno de caixa
    @ManyToOne
    private CaixaTurno turno;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MetodoPagamento metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigemPagamento origem;

    // Quanto este pagamento abate da conta
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    // Quanto o cliente entregou (em dinheiro pode ser mais que o valor; a diferença volta como troco)
    @Column(name = "valor_recebido", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorRecebido;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal troco = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StatusPagamento status = StatusPagamento.CONFIRMADO;

    @Column(name = "criado_por", nullable = false, updatable = false)
    private Long criadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @Version
    private Long version;

    protected Pagamento() {
    }

    public Pagamento(Comanda comanda, Long reservaId, CaixaTurno turno, MetodoPagamento metodo, OrigemPagamento origem,
            BigDecimal valor, BigDecimal valorRecebido, BigDecimal troco, Long criadoPor) {
        this.comanda = comanda;
        this.reservaId = reservaId;
        this.turno = turno;
        this.metodo = metodo;
        this.origem = origem;
        this.valor = valor;
        this.valorRecebido = valorRecebido;
        this.troco = troco;
        this.criadoPor = criadoPor;
    }

    public Long getId() { return id; }
    public Comanda getComanda() { return comanda; }
    public void setComanda(Comanda comanda) { this.comanda = comanda; }
    public Long getReservaId() { return reservaId; }
    public CaixaTurno getTurno() { return turno; }
    public MetodoPagamento getMetodo() { return metodo; }
    public OrigemPagamento getOrigem() { return origem; }
    public BigDecimal getValor() { return valor; }
    public BigDecimal getValorRecebido() { return valorRecebido; }
    public BigDecimal getTroco() { return troco; }
    public StatusPagamento getStatus() { return status; }
    public void setStatus(StatusPagamento status) { this.status = status; }
    public Long getCriadoPor() { return criadoPor; }
    public Instant getCriadoEm() { return criadoEm; }
}
