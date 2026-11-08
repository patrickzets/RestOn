package br.edu.reston.pedido;

import java.math.BigDecimal;

import br.edu.reston.cardapio.Produto;
import br.edu.reston.comanda.Comanda;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "item_pedido")
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Pedido pedido;

    // Comanda em que este item é cobrado
    @ManyToOne(optional = false)
    private Comanda comanda;

    @ManyToOne(optional = false)
    private Produto produto;

    @Column(nullable = false)
    private int quantidade;

    // Preço do produto na hora do pedido: mudar o cardápio depois não altera o que já foi pedido
    @Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoUnitario;

    @Column(length = 80)
    private String observacao;

    @Column(nullable = false)
    private boolean cancelado = false;

    @Column(name = "motivo_cancelamento", length = 120)
    private String motivoCancelamento;

    protected ItemPedido() {
    }

    public ItemPedido(Pedido pedido, Comanda comanda, Produto produto, int quantidade, String observacao) {
        this.pedido = pedido;
        this.comanda = comanda;
        this.produto = produto;
        this.quantidade = quantidade;
        this.precoUnitario = produto.getPreco();
        this.observacao = observacao;
    }

    public BigDecimal subtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public Comanda getComanda() { return comanda; }
    public void setComanda(Comanda comanda) { this.comanda = comanda; }
    public Produto getProduto() { return produto; }
    public int getQuantidade() { return quantidade; }
    public BigDecimal getPrecoUnitario() { return precoUnitario; }
    public String getObservacao() { return observacao; }
    public boolean isCancelado() { return cancelado; }
    public void setCancelado(boolean cancelado) { this.cancelado = cancelado; }
    public String getMotivoCancelamento() { return motivoCancelamento; }
    public void setMotivoCancelamento(String motivoCancelamento) { this.motivoCancelamento = motivoCancelamento; }
}
