package br.edu.reston.configuracao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "configuracao")
public class Configuracao {

    @Id
    private String chave;

    @Column(nullable = false)
    private String valor;

    protected Configuracao() {
    }

    public String getChave() { return chave; }
    public String getValor() { return valor; }
}
