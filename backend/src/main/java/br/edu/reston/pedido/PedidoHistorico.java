package br.edu.reston.pedido;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Cada mudança de status do pedido: de qual para qual, quem fez e quando
@Entity
@Table(name = "pedido_historico")
public class PedidoHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false, updatable = false)
    private Long pedidoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", length = 15, updatable = false)
    private StatusPedido statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false, length = 15, updatable = false)
    private StatusPedido statusNovo;

    @Column(name = "funcionario_id", nullable = false, updatable = false)
    private Long funcionarioId;

    @Column(nullable = false, updatable = false)
    private Instant em = Instant.now();

    protected PedidoHistorico() {
    }

    public PedidoHistorico(Long pedidoId, StatusPedido statusAnterior, StatusPedido statusNovo, Long funcionarioId) {
        this.pedidoId = pedidoId;
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        this.funcionarioId = funcionarioId;
    }

    public Long getPedidoId() { return pedidoId; }
    public StatusPedido getStatusAnterior() { return statusAnterior; }
    public StatusPedido getStatusNovo() { return statusNovo; }
    public Instant getEm() { return em; }
}
