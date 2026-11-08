package br.edu.reston.pedido;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import br.edu.reston.cardapio.DestinoPreparo;
import br.edu.reston.comanda.Comanda;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Comanda em que o pedido foi lançado (a cobrança de cada item pode mudar de comanda depois)
    @ManyToOne(optional = false)
    private Comanda comanda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private DestinoPreparo destino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatusPedido status;

    @Column(name = "criado_por", nullable = false, updatable = false)
    private Long criadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<ItemPedido> itens = new ArrayList<>();

    @Version
    private Long version;

    protected Pedido() {
    }

    public Pedido(Comanda comanda, DestinoPreparo destino, StatusPedido status, Long criadoPor) {
        this.comanda = comanda;
        this.destino = destino;
        this.status = status;
        this.criadoPor = criadoPor;
    }

    public void adicionar(ItemPedido item) {
        itens.add(item);
    }

    public Long getId() { return id; }
    public Comanda getComanda() { return comanda; }
    public DestinoPreparo getDestino() { return destino; }
    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }
    public Long getCriadoPor() { return criadoPor; }
    public Instant getCriadoEm() { return criadoEm; }
    public List<ItemPedido> getItens() { return itens; }
}
