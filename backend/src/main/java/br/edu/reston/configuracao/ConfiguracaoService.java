package br.edu.reston.configuracao;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfiguracaoService {

    private final ConfiguracaoRepository repositorio;

    public ConfiguracaoService(ConfiguracaoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public int minutos(String chave) {
        Configuracao c = repositorio.findById(chave)
                .orElseThrow(() -> new IllegalStateException("Configuração não encontrada: " + chave));
        return Integer.parseInt(c.getValor());
    }

    public BigDecimal taxaDeServico() {
        Configuracao c = repositorio.findById("taxa_servico_percentual")
                .orElseThrow(() -> new IllegalStateException("Configuração não encontrada: taxa_servico_percentual"));
        return new BigDecimal(c.getValor());
    }

    public int duracaoDaReserva() {
        return minutos("reserva_duracao_min");
    }

    public int toleranciaDaReserva() {
        return minutos("reserva_tolerancia_min");
    }
}
