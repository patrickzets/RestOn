package br.edu.reston.cardapio;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import br.edu.reston.BaseIntegracaoTest;
import br.edu.reston.funcionario.Perfil;

class CardapioIntegracaoTest extends BaseIntegracaoTest {

    // Cabeçalho de um PNG de verdade (o suficiente para o sistema reconhecer o tipo)
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

    private Integer criarCategoria(MockHttpSession sessao, String nome) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/categorias").with(csrf()).session(sessao)
                .contentType(MediaType.APPLICATION_JSON).content(json("nome", nome)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
    }

    private String corpoDoProduto(Integer categoria, String nome, String preco) {
        return "{\"categoriaId\":" + categoria + ",\"nome\":\"" + nome + "\",\"descricao\":\"Arroz cremoso\","
                + "\"preco\":" + preco + ",\"tempoPreparoMin\":18,\"destinoPreparo\":\"COZINHA\"}";
    }

    private Integer criarProduto(MockHttpSession sessao, Integer categoria, String nome, String preco) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/produtos").with(csrf()).session(sessao)
                .contentType(MediaType.APPLICATION_JSON).content(corpoDoProduto(categoria, nome, preco)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void garcomELeCaixaLeemOCardapioMasNaoMexem() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession garcom = criarEEntrar(admin, "gabi", Perfil.GARCOM);
        MockHttpSession caixa = criarEEntrar(admin, "caio", Perfil.CAIXA);
        MockHttpSession cozinha = criarEEntrar(admin, "cris", Perfil.COZINHA);
        Integer categoria = criarCategoria(admin, "Pratos");
        criarProduto(admin, categoria, "Risoto de funghi", "49.00");

        mvc.perform(get("/api/v1/produtos").session(garcom)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/v1/categorias").session(caixa)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/produtos").with(csrf()).session(garcom).contentType(MediaType.APPLICATION_JSON)
                .content(corpoDoProduto(categoria, "Outro", "10.00")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/categorias").with(csrf()).session(garcom).contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "Bebidas")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/produtos").session(cozinha)).andExpect(status().isForbidden());
    }

    @Test
    void gerenteCriaProdutoComPrecoExato() throws Exception {
        MockHttpSession gerente = criarEEntrar(entrarComoAdmin(), "gil", Perfil.GERENTE);
        Integer categoria = criarCategoria(gerente, "Pratos");

        mvc.perform(post("/api/v1/produtos").with(csrf()).session(gerente).contentType(MediaType.APPLICATION_JSON)
                .content(corpoDoProduto(categoria, "Risoto de funghi", "49.90")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preco").value(49.90))
                .andExpect(jsonPath("$.categoriaNome").value("Pratos"))
                .andExpect(jsonPath("$.disponivel").value(true))
                .andExpect(jsonPath("$.imagem").doesNotExist());
    }

    @Test
    void produtoComDadosInvalidosEhRecusado() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Integer categoria = criarCategoria(admin, "Pratos");

        mvc.perform(post("/api/v1/produtos").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(corpoDoProduto(categoria, "Risoto", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.preco").exists());

        mvc.perform(post("/api/v1/produtos").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(corpoDoProduto(categoria, "Risoto", "10.999")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.preco").exists());

        String descricaoLonga = "x".repeat(161);
        mvc.perform(post("/api/v1/produtos").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoriaId\":" + categoria + ",\"nome\":\"Risoto\",\"descricao\":\"" + descricaoLonga
                        + "\",\"preco\":10,\"tempoPreparoMin\":5,\"destinoPreparo\":\"COZINHA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.descricao").exists());
    }

    @Test
    void nomeDeProdutoERepetidoEhRecusado() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Integer categoria = criarCategoria(admin, "Pratos");
        criarProduto(admin, categoria, "Risoto de funghi", "49.00");

        mvc.perform(post("/api/v1/produtos").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(corpoDoProduto(categoria, "RISOTO DE FUNGHI", "50.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Já existe")));
    }

    @Test
    void atualizaProdutoETrocaDeCategoria() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Integer pratos = criarCategoria(admin, "Pratos");
        Integer bebidas = criarCategoria(admin, "Bebidas");
        Integer id = criarProduto(admin, pratos, "Suco de laranja", "8.00");

        mvc.perform(put("/api/v1/produtos/" + id).with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoriaId\":" + bebidas + ",\"nome\":\"Suco de laranja\",\"preco\":9.50,"
                        + "\"tempoPreparoMin\":3,\"destinoPreparo\":\"BAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaNome").value("Bebidas"))
                .andExpect(jsonPath("$.destinoPreparo").value("BAR"))
                .andExpect(jsonPath("$.descricao").doesNotExist());
    }

    @Test
    void marcarIndisponivelNaoApagaOProduto() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Integer id = criarProduto(admin, criarCategoria(admin, "Pratos"), "Moqueca", "74.00");

        mvc.perform(patch("/api/v1/produtos/" + id + "/disponibilidade").with(csrf()).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"disponivel\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel").value(false));

        mvc.perform(get("/api/v1/produtos").session(admin)).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void produtoArquivadoSomeParaOGarcomMasNaoParaOGerente() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession garcom = criarEEntrar(admin, "gabi", Perfil.GARCOM);
        Integer id = criarProduto(admin, criarCategoria(admin, "Pratos"), "Moqueca", "74.00");

        mvc.perform(patch("/api/v1/produtos/" + id + "/ativo").with(csrf()).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mvc.perform(get("/api/v1/produtos").session(garcom)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/produtos").session(admin)).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void categoriasSeReordenamEContamSoProdutosAtivos() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Integer pratos = criarCategoria(admin, "Pratos");
        criarCategoria(admin, "Bebidas");
        criarProduto(admin, pratos, "Moqueca", "74.00");

        mvc.perform(get("/api/v1/categorias").session(admin))
                .andExpect(jsonPath("$[0].nome").value("Pratos"))
                .andExpect(jsonPath("$[0].qtdProdutos").value(1))
                .andExpect(jsonPath("$[1].qtdProdutos").value(0));

        mvc.perform(post("/api/v1/categorias/" + pratos + "/descer").with(csrf()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Bebidas"))
                .andExpect(jsonPath("$[1].nome").value("Pratos"));
    }

    @Test
    void naoExcluiCategoriaComProdutosMasExcluiAVazia() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Integer pratos = criarCategoria(admin, "Pratos");
        Integer vazia = criarCategoria(admin, "Sobremesas");
        criarProduto(admin, pratos, "Moqueca", "74.00");

        mvc.perform(delete("/api/v1/categorias/" + pratos).with(csrf()).session(admin))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("tem produtos")));
        mvc.perform(delete("/api/v1/categorias/" + vazia).with(csrf()).session(admin)).andExpect(status().isNoContent());
    }

    @Test
    void categoriaComNomeRepetidoEhRecusada() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        criarCategoria(admin, "Pratos");

        mvc.perform(post("/api/v1/categorias").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "pratos")))
                .andExpect(status().isConflict());
    }

    @Test
    void enviaTrocaERemoveAFotoDoProduto() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession garcom = criarEEntrar(admin, "gabi", Perfil.GARCOM);
        Integer id = criarProduto(admin, criarCategoria(admin, "Pratos"), "Risoto", "49.00");

        // Sem foto ainda
        mvc.perform(get("/api/v1/produtos/" + id + "/imagem").session(admin)).andExpect(status().isNotFound());

        MockMultipartFile foto = new MockMultipartFile("arquivo", "risoto.png", "image/png", PNG);
        MvcResult enviada = mvc.perform(multipart("/api/v1/produtos/" + id + "/imagem").file(foto).with(csrf()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagem").value(containsString(".png")))
                .andReturn();
        String nomeDoArquivo = JsonPath.read(enviada.getResponse().getContentAsString(), "$.imagem");

        // Qualquer perfil do salão consegue ver a foto
        mvc.perform(get("/api/v1/produtos/" + id + "/imagem").session(garcom))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));

        // Trocar gera outro nome de arquivo
        MvcResult trocada = mvc.perform(multipart("/api/v1/produtos/" + id + "/imagem").file(foto).with(csrf()).session(admin))
                .andReturn();
        String novoNome = JsonPath.read(trocada.getResponse().getContentAsString(), "$.imagem");
        org.junit.jupiter.api.Assertions.assertNotEquals(nomeDoArquivo, novoNome);

        mvc.perform(delete("/api/v1/produtos/" + id + "/imagem").with(csrf()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagem").doesNotExist());
        mvc.perform(get("/api/v1/produtos/" + id + "/imagem").session(admin)).andExpect(status().isNotFound());
    }

    @Test
    void arquivoQueNaoEImagemEhRecusadoMesmoComExtensaoDeImagem() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        Integer id = criarProduto(admin, criarCategoria(admin, "Pratos"), "Risoto", "49.00");

        MockMultipartFile falso = new MockMultipartFile("arquivo", "virus.png", "image/png", "isto é só texto".getBytes());
        mvc.perform(multipart("/api/v1/produtos/" + id + "/imagem").file(falso).with(csrf()).session(admin))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("JPG, PNG ou WebP")));
    }

    @Test
    void garcomNaoEnviaFoto() throws Exception {
        MockHttpSession admin = entrarComoAdmin();
        MockHttpSession garcom = criarEEntrar(admin, "gabi", Perfil.GARCOM);
        Integer id = criarProduto(admin, criarCategoria(admin, "Pratos"), "Risoto", "49.00");

        MockMultipartFile foto = new MockMultipartFile("arquivo", "risoto.png", "image/png", PNG);
        mvc.perform(multipart("/api/v1/produtos/" + id + "/imagem").file(foto).with(csrf()).session(garcom))
                .andExpect(status().isForbidden());
    }
}
