package br.edu.reston.tempo;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// Mantém as telas abertas (cozinha, mesas) avisadas quando algo muda. O aviso não leva dados:
// a tela só recarrega o que precisa pela API normal, então as regras de permissão continuam valendo.
@Service
public class TempoRealService {

    public static final String PEDIDOS = "pedidos";
    public static final String MESAS = "mesas";

    private static final long TEMPO_MAXIMO_MS = 30 * 60 * 1000L;

    private final Map<String, List<SseEmitter>> inscritos = Map.of(
            PEDIDOS, new CopyOnWriteArrayList<>(),
            MESAS, new CopyOnWriteArrayList<>());

    public SseEmitter inscrever(String canal) {
        List<SseEmitter> lista = inscritos.get(canal);
        SseEmitter conexao = new SseEmitter(TEMPO_MAXIMO_MS);
        lista.add(conexao);
        conexao.onCompletion(() -> lista.remove(conexao));
        conexao.onTimeout(() -> lista.remove(conexao));
        conexao.onError(erro -> lista.remove(conexao));

        try {
            conexao.send(SseEmitter.event().name("conectado").data(canal));
        } catch (IOException erro) {
            lista.remove(conexao);
        }
        return conexao;
    }

    // Só avisa depois que a transação foi gravada no banco; se ela desfizer, ninguém é avisado
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoMudarPedidos(PedidosAlterados evento) {
        avisar(PEDIDOS);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoMudarMesas(MesasAlteradas evento) {
        avisar(MESAS);
    }

    // Um sinal a cada 20 segundos mantém a conexão viva (roteadores e proxies fecham conexões paradas)
    @Scheduled(fixedRate = 20000)
    void batimento() {
        inscritos.values().forEach(lista -> lista.forEach(conexao -> enviar(lista, conexao, SseEmitter.event().comment("batimento"))));
    }

    int totalDeInscritos(String canal) {
        return inscritos.get(canal).size();
    }

    private void avisar(String canal) {
        List<SseEmitter> lista = inscritos.get(canal);
        lista.forEach(conexao -> enviar(lista, conexao, SseEmitter.event().name("mudou").data(canal)));
    }

    private void enviar(List<SseEmitter> lista, SseEmitter conexao, SseEmitter.SseEventBuilder evento) {
        try {
            conexao.send(evento);
        } catch (IOException | IllegalStateException erro) {
            // A pessoa fechou a tela: tira da lista
            lista.remove(conexao);
        }
    }
}
