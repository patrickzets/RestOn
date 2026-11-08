package br.edu.reston.estoque;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "movimentacao_estoque")
public class MovimentacaoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "produto_id", nullable = false, updatable = false)
    private Long produtoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TipoMovimentacao tipo;

    // Positivo entra, negativo sai
    @Column(nullable = false, updatable = false)
    private int variacao;

    @Column(name = "saldo_apos", nullable = false, updatable = false)
    private int saldoApos;

    @Column(length = 120, updatable = false)
    private String motivo;

    @Column(name = "funcionario_id", updatable = false)
    private Long funcionarioId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    protected MovimentacaoEstoque() {
    }

    public MovimentacaoEstoque(Long produtoId, TipoMovimentacao tipo, int variacao, int saldoApos, String motivo, Long funcionarioId) {
        this.produtoId = produtoId;
        this.tipo = tipo;
        this.variacao = variacao;
        this.saldoApos = saldoApos;
        this.motivo = motivo;
        this.funcionarioId = funcionarioId;
    }

    public Long getId() { return id; }
    public Long getProdutoId() { return produtoId; }
    public TipoMovimentacao getTipo() { return tipo; }
    public int getVariacao() { return variacao; }
    public int getSaldoApos() { return saldoApos; }
    public String getMotivo() { return motivo; }
    public Long getFuncionarioId() { return funcionarioId; }
    public Instant getCriadoEm() { return criadoEm; }
}
