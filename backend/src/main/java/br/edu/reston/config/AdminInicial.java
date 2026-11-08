package br.edu.reston.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.funcionario.Funcionario;
import br.edu.reston.funcionario.FuncionarioRepository;
import br.edu.reston.funcionario.Perfil;
import br.edu.reston.funcionario.PoliticaSenha;
import br.edu.reston.funcionario.SenhaProvisoria;

// Se o banco está sem funcionários, cria o admin. Sem RESTON_ADMIN_SENHA, gera uma
// senha provisória e mostra no log só desta vez.
@Component
@Order(1)
public class AdminInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicial.class);

    private final FuncionarioRepository funcionarios;
    private final PasswordEncoder codificador;
    private final AuditoriaService auditoria;
    private final String login;
    private final String senhaConfigurada;

    public AdminInicial(FuncionarioRepository funcionarios, PasswordEncoder codificador, AuditoriaService auditoria,
            @Value("${reston.admin.login:admin}") String login,
            @Value("${reston.admin.senha:}") String senhaConfigurada) {
        this.funcionarios = funcionarios;
        this.codificador = codificador;
        this.auditoria = auditoria;
        this.login = login.trim().toLowerCase();
        this.senhaConfigurada = senhaConfigurada;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (funcionarios.count() > 0) {
            return;
        }

        boolean gerada = senhaConfigurada == null || senhaConfigurada.isBlank();
        String senha = gerada ? SenhaProvisoria.gerar() : senhaConfigurada;
        if (!gerada) {
            PoliticaSenha.validar(senha, login);
        }

        Funcionario admin = funcionarios.save(new Funcionario("Administrador", login, codificador.encode(senha), Perfil.ADMIN, gerada));
        auditoria.registrar(admin.getId(), admin.getLogin(), "ADMIN_INICIAL_CRIADO", "funcionario", String.valueOf(admin.getId()),
                gerada ? "senha provisória gerada" : "senha definida por variável de ambiente");

        if (gerada) {
            log.warn("""

                    ====== PRIMEIRO ACESSO ======
                    Login: {}
                    Senha provisória: {}
                    O sistema vai pedir para trocar a senha no primeiro login.
                    =============================""", admin.getLogin(), senha);
        }
    }
}
