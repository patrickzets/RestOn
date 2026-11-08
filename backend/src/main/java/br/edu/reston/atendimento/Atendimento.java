package br.edu.reston.atendimento;

import java.time.Instant;

import br.edu.reston.mesa.Mesa;
import br.edu.reston.reserva.Reserva;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "atendimento")
public class Atendimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Mesa mesa;

    // Só tem valor quando o atendimento começou pela chegada de uma reserva
    @ManyToOne
    private Reserva reserva;

    @Column(name = "aberto_por", nullable = false, updatable = false)
    private Long abertoPor;

    @Column(name = "aberto_em", nullable = false, updatable = false)
    private Instant abertoEm = Instant.now();

    @Column(name = "encerrado_em")
    private Instant encerradoEm;

    @Version
    private Long version;

    protected Atendimento() {
    }

    public Atendimento(Mesa mesa, Reserva reserva, Long abertoPor) {
        this.mesa = mesa;
        this.reserva = reserva;
        this.abertoPor = abertoPor;
    }

    public Long getId() { return id; }
    public Mesa getMesa() { return mesa; }
    public void setMesa(Mesa mesa) { this.mesa = mesa; }
    public Reserva getReserva() { return reserva; }
    public Long getAbertoPor() { return abertoPor; }
    public Instant getAbertoEm() { return abertoEm; }
    public Instant getEncerradoEm() { return encerradoEm; }
    public void setEncerradoEm(Instant encerradoEm) { this.encerradoEm = encerradoEm; }
}
