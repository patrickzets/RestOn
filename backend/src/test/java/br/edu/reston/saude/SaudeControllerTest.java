package br.edu.reston.saude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

class SaudeControllerTest {

    @Test
    void respondeOkQuandoOBancoResponde() throws Exception {
        Connection conexao = mock(Connection.class);
        when(conexao.isValid(anyInt())).thenReturn(true);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(conexao);

        var resposta = new SaudeController(dataSource).verificar();

        assertEquals(200, resposta.getStatusCode().value());
        assertEquals("ok", resposta.getBody().get("status"));
        assertTrue((Boolean) resposta.getBody().get("banco"));
    }

    @Test
    void responde503QuandoOBancoNaoResponde() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("sem conexão"));

        var resposta = new SaudeController(dataSource).verificar();

        assertEquals(503, resposta.getStatusCode().value());
        assertEquals("banco-indisponivel", resposta.getBody().get("status"));
    }
}
