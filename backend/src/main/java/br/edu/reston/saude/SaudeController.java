package br.edu.reston.saude;

import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.Map;
import javax.sql.DataSource;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/saude")
public class SaudeController {

    private final DataSource dataSource;

    public SaudeController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> verificar() {
        boolean bancoOk = bancoResponde();

        Map<String, Object> corpo = Map.of(
                "status", bancoOk ? "ok" : "banco-indisponivel",
                "banco", bancoOk,
                "versao", "0.0.1",
                "horario", OffsetDateTime.now().toString());

        if (bancoOk) {
            return ResponseEntity.ok(corpo);
        }
        return ResponseEntity.status(503).body(corpo);
    }

    private boolean bancoResponde() {
        try (Connection conexao = dataSource.getConnection()) {
            return conexao.isValid(2);
        } catch (Exception erro) {
            return false;
        }
    }
}
