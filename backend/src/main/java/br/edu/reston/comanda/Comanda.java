package br.edu.reston.comanda;

import java.math.BigDecimal;
import java.time.Instant;

import br.edu.reston.atendimento.Atendimento;
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
@Table(name = "comanda")
public class Comanda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String codigo;

    @ManyToOne(optional = false)
    private Atendimento atendimento;

    @Column(name = "numero_cliente", nullable = false)
    private int numeroCliente;

    @Column(name = "nome_cliente", length = 60)
    private String nomeCliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatusComanda status = StatusComanda.ABERTA;

    @Column(name = "garcom_id", nullable = false, updatable = false)
    private Long garcomId;

    @Column(name = "taxa_percentual", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxaPercentual;

    @Column(name = "taxa_removida", nullable = false)
    private boolean taxaRemovida = false;

    @Column(name = "aberta_em", nullable = false, updatable = false)
    private Instant abertaEm = Instant.now();

    @Column(name = "fechada_em")
    private Instant fechadaEm;

    // Preenchidos só no fechamento: depois de fechada, a conta nunca mais muda
    @Column(name = "subtotal_final", precision = 12, scale = 2)
    private BigDecimal subtotalFinal;

    @Column(name = "taxa_valor_final", precision = 12, scale = 2)
    private BigDecimal taxaValorFinal;

    @Column(name = "total_final", precision = 12, scale = 2)
    private BigDecimal totalFinal;

    @Version
    private Long version;

    protected Comanda() {
    }

    public Comanda(String codigo, Atendimento atendimento, int numeroCliente, String nomeCliente, Long garcomId,
            BigDecimal taxaPercentual) {
        this.codigo = codigo;
        this.atendimento = atendimento;
        this.numeroCliente = numeroCliente;
        this.nomeCliente = nomeCliente;
        this.garcomId = garcomId;
        this.taxaPercentual = taxaPercentual;
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public Atendimento getAtendimento() { return atendimento; }
    public int getNumeroCliente() { return numeroCliente; }
    public String getNomeCliente() { return nomeCliente; }
    public StatusComanda getStatus() { return status; }
    public void setStatus(StatusComanda status) { this.status = status; }
    public Long getGarcomId() { return garcomId; }
    public BigDecimal getTaxaPercentual() { return taxaPercentual; }
    public boolean isTaxaRemovida() { return taxaRemovida; }
    public void setTaxaRemovida(boolean taxaRemovida) { this.taxaRemovida = taxaRemovida; }
    public BigDecimal getSubtotalFinal() { return subtotalFinal; }
    public BigDecimal getTaxaValorFinal() { return taxaValorFinal; }
    public BigDecimal getTotalFinal() { return totalFinal; }

    public void fechar(BigDecimal subtotal, BigDecimal taxaValor, BigDecimal total) {
        this.status = StatusComanda.FECHADA;
        this.fechadaEm = Instant.now();
        this.subtotalFinal = subtotal;
        this.taxaValorFinal = taxaValor;
        this.totalFinal = total;
    }
    public Instant getAbertaEm() { return abertaEm; }
    public Instant getFechadaEm() { return fechadaEm; }
    public void setFechadaEm(Instant fechadaEm) { this.fechadaEm = fechadaEm; }
}
