package br.edu.reston.cardapio;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "produto")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Categoria categoria;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(length = 160)
    private String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    // Nome do arquivo da foto na pasta de uploads (null = sem foto)
    @Column(length = 60)
    private String imagem;

    @Column(name = "tempo_preparo_min", nullable = false)
    private int tempoPreparoMin;

    @Enumerated(EnumType.STRING)
    @Column(name = "destino_preparo", nullable = false, length = 15)
    private DestinoPreparo destinoPreparo;

    // Disponível = dá para vender agora. Ativo = faz parte do cardápio (arquivado some para a equipe).
    @Column(nullable = false)
    private boolean disponivel = true;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "controla_estoque", nullable = false)
    private boolean controlaEstoque = false;

    @Column(name = "estoque_atual", nullable = false)
    private int estoqueAtual = 0;

    @Column(name = "estoque_minimo", nullable = false)
    private int estoqueMinimo = 0;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Version
    private Long version;

    protected Produto() {
    }

    public Produto(Categoria categoria, String nome, String descricao, BigDecimal preco, int tempoPreparoMin,
            DestinoPreparo destinoPreparo) {
        this.categoria = categoria;
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
        this.tempoPreparoMin = tempoPreparoMin;
        this.destinoPreparo = destinoPreparo;
    }

    @PrePersist
    void antesDeInserir() {
        criadoEm = Instant.now();
        atualizadoEm = criadoEm;
    }

    @PreUpdate
    void antesDeAtualizar() {
        atualizadoEm = Instant.now();
    }

    public Long getId() { return id; }
    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }
    public String getImagem() { return imagem; }
    public void setImagem(String imagem) { this.imagem = imagem; }
    public int getTempoPreparoMin() { return tempoPreparoMin; }
    public void setTempoPreparoMin(int tempoPreparoMin) { this.tempoPreparoMin = tempoPreparoMin; }
    public DestinoPreparo getDestinoPreparo() { return destinoPreparo; }
    public void setDestinoPreparo(DestinoPreparo destinoPreparo) { this.destinoPreparo = destinoPreparo; }
    public boolean isDisponivel() { return disponivel; }
    public void setDisponivel(boolean disponivel) { this.disponivel = disponivel; }
    public boolean isControlaEstoque() { return controlaEstoque; }
    public void setControlaEstoque(boolean controlaEstoque) { this.controlaEstoque = controlaEstoque; }
    public int getEstoqueAtual() { return estoqueAtual; }
    public void setEstoqueAtual(int estoqueAtual) { this.estoqueAtual = estoqueAtual; }
    public int getEstoqueMinimo() { return estoqueMinimo; }
    public void setEstoqueMinimo(int estoqueMinimo) { this.estoqueMinimo = estoqueMinimo; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
