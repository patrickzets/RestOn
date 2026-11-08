package br.edu.reston.reserva;

import java.time.Instant;

import br.edu.reston.mesa.Mesa;
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
@Table(name = "reserva")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Mesa mesa;

    @Column(name = "nome_cliente", nullable = false, length = 120)
    private String nomeCliente;

    @Column(length = 20)
    private String telefone;

    @Column(name = "qtd_pessoas", nullable = false)
    private int qtdPessoas;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatusReserva status = StatusReserva.ATIVA;

    @Column(name = "criado_por", nullable = false, updatable = false)
    private Long criadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "anonimizado_em")
    private Instant anonimizadoEm;

    @Version
    private Long version;

    protected Reserva() {
    }

    public Reserva(Mesa mesa, String nomeCliente, String telefone, int qtdPessoas, Instant inicio, Instant fim, Long criadoPor) {
        this.mesa = mesa;
        this.nomeCliente = nomeCliente;
        this.telefone = telefone;
        this.qtdPessoas = qtdPessoas;
        this.inicio = inicio;
        this.fim = fim;
        this.criadoPor = criadoPor;
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

    // Nome e telefone saem depois do prazo de guarda (LGPD); a reserva em si continua para os relatórios
    public void anonimizar() {
        nomeCliente = "Cliente anonimizado";
        telefone = null;
        anonimizadoEm = Instant.now();
    }

    public Instant getAnonimizadoEm() { return anonimizadoEm; }
    public Long getId() { return id; }
    public Mesa getMesa() { return mesa; }
    public void setMesa(Mesa mesa) { this.mesa = mesa; }
    public String getNomeCliente() { return nomeCliente; }
    public void setNomeCliente(String nomeCliente) { this.nomeCliente = nomeCliente; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public int getQtdPessoas() { return qtdPessoas; }
    public void setQtdPessoas(int qtdPessoas) { this.qtdPessoas = qtdPessoas; }
    public Instant getInicio() { return inicio; }
    public void setInicio(Instant inicio) { this.inicio = inicio; }
    public Instant getFim() { return fim; }
    public void setFim(Instant fim) { this.fim = fim; }
    public StatusReserva getStatus() { return status; }
    public void setStatus(StatusReserva status) { this.status = status; }
    public Instant getCriadoEm() { return criadoEm; }
}
