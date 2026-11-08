package br.edu.reston.reserva;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import br.edu.reston.BaseIntegracaoTest;
import br.edu.reston.funcionario.Perfil;

class ReservaIntegracaoTest extends BaseIntegracaoTest {

    @Autowired
    private JdbcTemplate jdbc;

    private Instant emDias(int dias, int hora) {
        return Instant.now().plus(dias, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(hora, ChronoUnit.HOURS);
    }

    private String corpo(Long mesa, int pessoas, Instant inicio) {
        return "{\"mesaId\":" + mesa + ",\"nomeCliente\":\"Marina Alves\",\"telefone\":\"(11) 91234-5678\","
                + "\"qtdPessoas\":" + pessoas + ",\"inicio\":\"" + inicio + "\"}";
    }

    private Integer criar(MockHttpSession sessao, Long mesa, int pessoas, Instant inicio) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/reservas").with(csrf()).session(sessao)
                .contentType(MediaType.APPLICATION_JSON).content(corpo(mesa, pessoas, inicio)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void criaReservaComDuracaoPadraoEMostraOTelefoneSoParaQuemPode() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession garcom = criarEEntrar(admin, "gabi", Perfil.GARCOM);
        Long mesa = criarMesa(admin, 1, 4);
        Instant inicio = emDias(2, 20);

        criar(garcom, mesa, 3, inicio);

        String de = inicio.minus(1, ChronoUnit.HOURS).toString();
        String ate = inicio.plus(1, ChronoUnit.HOURS).toString();

        mvc.perform(get("/api/v1/reservas?de=" + de + "&ate=" + ate).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].telefone").value("11912345678"))
                .andExpect(jsonPath("$[0].fim").value(inicio.plus(120, ChronoUnit.MINUTES).toString()));

        // Garçom cria, mas não enxerga o telefone (LGPD)
        mvc.perform(get("/api/v1/reservas?de=" + de + "&ate=" + ate).session(garcom))
                .andExpect(jsonPath("$[0].telefone").doesNotExist())
                .andExpect(jsonPath("$[0].nomeCliente").value("Marina Alves"));
    }

    @Test
    void garcomEditaAReservaSemApagarOTelefone() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession garcom = criarEEntrar(admin, "gabi", Perfil.GARCOM);
        Long mesa = criarMesa(admin, 1, 6);
        Instant inicio = emDias(2, 20);
        Integer id = criar(garcom, mesa, 2, inicio);

        // O garçom não vê o telefone, então edita sem enviá-lo
        mvc.perform(put("/api/v1/reservas/" + id).with(csrf()).session(garcom).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mesaId\":" + mesa + ",\"nomeCliente\":\"Marina A.\",\"qtdPessoas\":4,\"inicio\":\"" + inicio + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qtdPessoas").value(4));

        String de = inicio.minus(1, ChronoUnit.HOURS).toString();
        String ate = inicio.plus(1, ChronoUnit.HOURS).toString();
        mvc.perform(get("/api/v1/reservas?de=" + de + "&ate=" + ate).session(admin))
                .andExpect(jsonPath("$[0].telefone").value("11912345678"))
                .andExpect(jsonPath("$[0].nomeCliente").value("Marina A."));
    }

    @Test
    void criarReservaSemTelefoneEhRecusado() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa = criarMesa(admin, 1, 4);

        mvc.perform(post("/api/v1/reservas").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mesaId\":" + mesa + ",\"nomeCliente\":\"Marina\",\"qtdPessoas\":2,\"inicio\":\"" + emDias(2, 20) + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("telefone")));
    }

    @Test
    void reservaQueSeCruzaComOutraNaMesmaMesaEhRecusada() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa = criarMesa(admin, 1, 4);
        Instant inicio = emDias(2, 20);
        criar(admin, mesa, 2, inicio);

        mvc.perform(post("/api/v1/reservas").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(mesa, 2, inicio.plus(60, ChronoUnit.MINUTES))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("já tem uma reserva")));
    }

    @Test
    void reservaQueComecaQuandoAOutraTerminaEhAceita() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa = criarMesa(admin, 1, 4);
        Instant inicio = emDias(2, 18);
        criar(admin, mesa, 2, inicio);

        criar(admin, mesa, 2, inicio.plus(120, ChronoUnit.MINUTES));
    }

    @Test
    void mesmoHorarioEmOutraMesaEhAceito() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa1 = criarMesa(admin, 1, 4);
        Long mesa2 = criarMesa(admin, 2, 4);
        Instant inicio = emDias(2, 20);

        criar(admin, mesa1, 2, inicio);
        criar(admin, mesa2, 2, inicio);
    }

    @Test
    void reservaAcimaDaCapacidadeEhRecusada() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa = criarMesa(admin, 1, 4);

        mvc.perform(post("/api/v1/reservas").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(mesa, 5, emDias(2, 20))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("no máximo 4")));
    }

    @Test
    void reservaNoPassadoEhRecusada() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa = criarMesa(admin, 1, 4);

        mvc.perform(post("/api/v1/reservas").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(mesa, 2, Instant.now().minus(1, ChronoUnit.DAYS))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("futuro")));
    }

    @Test
    void telefoneInvalidoEhRecusado() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa = criarMesa(admin, 1, 4);

        mvc.perform(post("/api/v1/reservas").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mesaId\":" + mesa + ",\"nomeCliente\":\"Marina\",\"telefone\":\"123\",\"qtdPessoas\":2,\"inicio\":\""
                        + emDias(2, 20) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.telefone").exists());
    }

    @Test
    void cancelarLiberaOHorarioParaOutraReserva() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa = criarMesa(admin, 1, 4);
        Instant inicio = emDias(2, 20);
        Integer id = criar(admin, mesa, 2, inicio);

        mvc.perform(post("/api/v1/reservas/" + id + "/cancelar").with(csrf()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));

        criar(admin, mesa, 2, inicio);

        // Cancelar de novo não pode
        mvc.perform(post("/api/v1/reservas/" + id + "/cancelar").with(csrf()).session(admin))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void chegadaDoClienteAbreOAtendimentoNaMesaReservada() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa = criarMesa(admin, 1, 4);
        Integer id = criar(admin, mesa, 2, Instant.now().plus(30, ChronoUnit.MINUTES));

        // A mesa já aparece como reservada no painel
        mvc.perform(get("/api/v1/mesas").session(admin))
                .andExpect(jsonPath("$[0].reservaProxima.nomeCliente").value("Marina Alves"));

        mvc.perform(post("/api/v1/reservas/" + id + "/chegada").with(csrf()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value(id));

        mvc.perform(get("/api/v1/mesas").session(admin))
                .andExpect(jsonPath("$[0].estado").value("OCUPADA"))
                .andExpect(jsonPath("$[0].reservaProxima").doesNotExist());
    }

    @Test
    void naoRegistraChegadaMuitoAntesDoHorario() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa = criarMesa(admin, 1, 4);
        Integer id = criar(admin, mesa, 2, emDias(3, 20));

        mvc.perform(post("/api/v1/reservas/" + id + "/chegada").with(csrf()).session(admin))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("cedo")));
    }

    @Test
    void mesaComReservaChegandoNaoRecebeClienteSemReserva() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa = criarMesa(admin, 1, 4);
        criar(admin, mesa, 2, Instant.now().plus(30, ChronoUnit.MINUTES));

        mvc.perform(post("/api/v1/mesas/" + mesa + "/atendimentos").with(csrf()).session(admin))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("reserva chegando")));
    }

    @Test
    void disponibilidadeTiraMesaPequenaEMesaJaReservada() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long pequena = criarMesa(admin, 1, 2);
        Long reservada = criarMesa(admin, 2, 6);
        criarMesa(admin, 3, 6);
        Instant inicio = emDias(2, 20);
        criar(admin, reservada, 4, inicio);

        mvc.perform(get("/api/v1/mesas/disponibilidade?inicio=" + inicio + "&pessoas=4").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].numero").value(3));
    }

    @Test
    void oProprioBancoRecusaReservasQueSeCruzam() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Long mesa = criarMesa(admin, 1, 4);
        Long autor = idDoFuncionario(admin, "admin");
        Instant inicio = emDias(2, 20);
        String sql = "insert into reserva (mesa_id, nome_cliente, telefone, qtd_pessoas, inicio, fim, criado_por) "
                + "values (?, 'Teste', '11912345678', 2, ?, ?, ?)";
        var inicioSql = java.sql.Timestamp.from(inicio);
        var fimSql = java.sql.Timestamp.from(inicio.plus(2, ChronoUnit.HOURS));
        jdbc.update(sql, mesa, inicioSql, fimSql, autor);

        var meioSql = java.sql.Timestamp.from(inicio.plus(1, ChronoUnit.HOURS));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, mesa, meioSql, fimSql, autor));
    }
}
