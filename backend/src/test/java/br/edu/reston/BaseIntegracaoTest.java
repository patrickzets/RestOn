package br.edu.reston;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import br.edu.reston.funcionario.Perfil;

/**
 * Base dos testes de integração: sobe o Spring inteiro contra um PostgreSQL de verdade (Testcontainers).
 * Cada teste roda dentro de uma transação que é desfeita no fim, então um teste não atrapalha o outro.
 */
@SpringBootTest(properties = { "reston.admin.senha=" + BaseIntegracaoTest.SENHA_ADMIN, "reston.upload.diretorio=target/uploads-teste" })
@Import(TestcontainersConfiguration.class)
@Transactional
public abstract class BaseIntegracaoTest {

    public static final String SENHA_ADMIN = "Reston#Teste2026";
    protected static final String SENHA_NOVA = "SenhaNova#2026x";

    @Autowired
    private WebApplicationContext contexto;

    protected MockMvc mvc;

    @BeforeEach
    void montarMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    protected String json(String... paresChaveValor) {
        StringBuilder texto = new StringBuilder("{");
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            if (i > 0) {
                texto.append(",");
            }
            texto.append("\"").append(paresChaveValor[i]).append("\":\"").append(paresChaveValor[i + 1]).append("\"");
        }
        return texto.append("}").toString();
    }

    /** Faz login e devolve a sessão (o "cookie") para usar nas próximas chamadas. */
    protected MockHttpSession entrar(String login, String senha) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("login", login, "senha", senha)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    protected MockHttpSession entrarComoAdmin() throws Exception {
        return entrar("admin", SENHA_ADMIN);
    }

    /** Cria um funcionário pela API e devolve a senha provisória que ele recebeu. */
    protected String criarFuncionario(MockHttpSession quemCria, String login, Perfil perfil) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/funcionarios").with(csrf()).session(quemCria)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "Pessoa " + login, "login", login, "perfil", perfil.name())))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.senhaProvisoria");
    }

    /** Cria o funcionário, faz o primeiro login e troca a senha provisória. Devolve a sessão já liberada. */
    protected MockHttpSession criarEEntrar(MockHttpSession quemCria, String login, Perfil perfil) throws Exception {
        String provisoria = criarFuncionario(quemCria, login, perfil);
        MockHttpSession sessao = entrar(login, provisoria);
        mvc.perform(post("/api/v1/auth/alterar-senha").with(csrf()).session(sessao)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("senhaAtual", provisoria, "novaSenha", SENHA_NOVA)))
                .andExpect(status().isNoContent());
        return sessao;
    }

    /** Cadastra uma mesa pela API e devolve o id dela. */
    protected Long criarMesa(MockHttpSession quemCria, int numero, int capacidade) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/mesas").with(csrf()).session(quemCria)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":" + numero + ",\"capacidade\":" + capacidade + ",\"ativa\":true}"))
                .andExpect(status().isCreated())
                .andReturn();
        Integer id = JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    /** Procura o id de um funcionário pelo login, usando a listagem da API. */
    protected Long idDoFuncionario(MockHttpSession quemConsulta, String login) throws Exception {
        String lista = mvc.perform(get("/api/v1/funcionarios").session(quemConsulta)).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(lista, "$[?(@.login=='" + login + "')].id");
        return ids.get(0).longValue();
    }
}
