package br.edu.reston.reserva;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import br.edu.reston.BaseIntegracaoTest;

class ReservaRotinasTest extends BaseIntegracaoTest {

    @Autowired
    private ReservaRotinas rotinas;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager gerenciador;

    // Cria pela API (que só aceita datas futuras) e depois muda o horário direto no banco
    private Long reservaComHorario(MockHttpSession admin, Long mesa, int diasAtras, int dia, String status) throws Exception {
        Instant futuro = Instant.now().plus(30 + dia, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        MvcResult r = mvc.perform(post("/api/v1/reservas").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mesaId\":" + mesa + ",\"nomeCliente\":\"Marina Alves\",\"telefone\":\"11912345678\",\"qtdPessoas\":2,\"inicio\":\""
                        + futuro + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        Number id = JsonPath.read(r.getResponse().getContentAsString(), "$.id");

        Instant fim = Instant.now().minus(diasAtras, ChronoUnit.DAYS).minus(10, ChronoUnit.MINUTES);
        jdbc.update("update reserva set inicio = ?, fim = ?, status = ? where id = ?",
                Timestamp.from(fim.minus(2, ChronoUnit.HOURS)), Timestamp.from(fim), status, id.longValue());
        gerenciador.clear();
        return id.longValue();
    }

    private Map<String, Object> linha(Long id) {
        gerenciador.flush();
        return jdbc.queryForMap("select status, nome_cliente, telefone, anonimizado_em from reserva where id = ?", id);
    }

    @Test
    void reservaAtivaQueJaPassouViraNaoCompareceuEAFuturaFicaComoEsta() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa = criarMesa(admin, 1, 4);
        Long passada = reservaComHorario(admin, mesa, 0, 0, "ATIVA");
        MvcResult futura = mvc.perform(post("/api/v1/reservas").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mesaId\":" + mesa + ",\"nomeCliente\":\"Rui\",\"telefone\":\"11999990000\",\"qtdPessoas\":2,\"inicio\":\""
                        + Instant.now().plus(3, ChronoUnit.DAYS) + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        Number idFutura = JsonPath.read(futura.getResponse().getContentAsString(), "$.id");

        assertEquals(1, rotinas.marcarNaoComparecimentos());

        assertEquals("NAO_COMPARECEU", linha(passada).get("status"));
        assertEquals("ATIVA", linha(idFutura.longValue()).get("status"));
        gerenciador.flush();
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_log where acao = 'RESERVA_NAO_COMPARECEU'", Integer.class));
        assertEquals(0, rotinas.marcarNaoComparecimentos());
    }

    @Test
    void anonimizaSoReservasEncerradasHaMaisDoPrazoEGuardaORestoDaReserva() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa = criarMesa(admin, 1, 4);
        Long antiga = reservaComHorario(admin, mesa, 100, 0, "CONCLUIDA");
        Long antigaCancelada = reservaComHorario(admin, mesa, 120, 1, "CANCELADA");
        Long antigaNaoCompareceu = reservaComHorario(admin, mesa, 95, 2, "NAO_COMPARECEU");
        Long recente = reservaComHorario(admin, mesa, 30, 3, "CONCLUIDA");
        Long antigaAindaAtiva = reservaComHorario(admin, mesa, 100, 4, "ATIVA");

        assertEquals(3, rotinas.anonimizarReservasAntigas());

        for (Long id : new Long[] { antiga, antigaCancelada, antigaNaoCompareceu }) {
            Map<String, Object> r = linha(id);
            assertEquals("Cliente anonimizado", r.get("nome_cliente"));
            assertNull(r.get("telefone"));
            assertNotNull(r.get("anonimizado_em"));
        }
        assertEquals("Marina Alves", linha(recente).get("nome_cliente"));
        assertEquals("11912345678", linha(recente).get("telefone"));
        assertEquals("Marina Alves", linha(antigaAindaAtiva).get("nome_cliente"));
        // A reserva continua existindo para os relatórios
        assertEquals("CONCLUIDA", linha(antiga).get("status"));

        // Rodar de novo não mexe em nada
        assertEquals(0, rotinas.anonimizarReservasAntigas());
        gerenciador.flush();
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_log where acao = 'RESERVAS_ANONIMIZADAS'", Integer.class));
    }
}
