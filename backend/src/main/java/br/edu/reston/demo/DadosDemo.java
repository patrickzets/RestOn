package br.edu.reston.demo;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.edu.reston.atendimento.AtendimentoService;
import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.caixa.MetodoPagamento;
import br.edu.reston.caixa.PagamentoService;
import br.edu.reston.caixa.CaixaService;
import br.edu.reston.cardapio.ArmazenamentoDeImagens;
import br.edu.reston.cardapio.CategoriaService;
import br.edu.reston.cardapio.DestinoPreparo;
import br.edu.reston.cardapio.Produto;
import br.edu.reston.cardapio.ProdutoRepository;
import br.edu.reston.cardapio.ProdutoService;
import br.edu.reston.cardapio.dto.CategoriaRequest;
import br.edu.reston.cardapio.dto.ProdutoRequest;
import br.edu.reston.comanda.ComandaService;
import br.edu.reston.estoque.EstoqueService;
import br.edu.reston.estoque.dto.AjusteRequest;
import br.edu.reston.estoque.dto.ControleRequest;
import br.edu.reston.funcionario.Funcionario;
import br.edu.reston.funcionario.FuncionarioRepository;
import br.edu.reston.funcionario.Perfil;
import br.edu.reston.funcionario.PoliticaSenha;
import br.edu.reston.mesa.MesaService;
import br.edu.reston.mesa.dto.MesaRequest;
import br.edu.reston.pedido.PedidoService;
import br.edu.reston.pedido.StatusPedido;
import br.edu.reston.pedido.dto.ItemRequest;
import br.edu.reston.pedido.dto.PedidoResposta;
import br.edu.reston.reserva.ReservaService;
import br.edu.reston.reserva.dto.ReservaRequest;

// Modo demonstração: só roda com reston.demo.ativo=true (variável RESTON_DEMO_ATIVO=true).
// Preenche um banco vazio com funcionários, cardápio, estoque, reservas, vendas dos últimos dias e
// um restaurante "ao vivo" (mesas ocupadas, pedidos na cozinha e no bar, caixa aberto).
@Component
@Order(10)
@ConditionalOnProperty(name = "reston.demo.ativo", havingValue = "true")
public class DadosDemo implements ApplicationRunner {

    // Senha de todos os funcionários de demonstração (só para apresentação, nunca para uso real)
    public static final String SENHA_DEMO = "Demo@Reston2026";

    private static final Logger log = LoggerFactory.getLogger(DadosDemo.class);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final String LOGIN_MARCADOR = "gerente";
    private static final int DIAS_DE_HISTORICO = 10;

    // nome, categoria, descrição, preço, preparo (min), destino, foto
    private record ItemDoCardapio(String nome, String categoria, String descricao, String preco, int preparo,
            DestinoPreparo destino, String foto) {
    }

    private final FuncionarioRepository funcionarios;
    private final ProdutoRepository produtos;
    private final PasswordEncoder codificador;
    private final MesaService mesaService;
    private final CategoriaService categoriaService;
    private final ProdutoService produtoService;
    private final EstoqueService estoqueService;
    private final ReservaService reservaService;
    private final AtendimentoService atendimentoService;
    private final ComandaService comandaService;
    private final PedidoService pedidoService;
    private final CaixaService caixaService;
    private final PagamentoService pagamentoService;
    private final AuditoriaService auditoria;
    private final ArmazenamentoDeImagens imagens;
    private final JdbcTemplate jdbc;
    private final EntityManager gerenciador;
    private final TransactionTemplate transacao;
    private final boolean producao;

    // Pedidos do estado "ao vivo": o horário de chegada é ajustado no fim
    private record Chegada(Long pedidoId, int minutosAtras) {
    }

    private final List<Chegada> chegadas = new ArrayList<>();
    private final Map<String, Long> idsDosProdutos = new HashMap<>();

    public DadosDemo(FuncionarioRepository funcionarios, ProdutoRepository produtos, PasswordEncoder codificador,
            MesaService mesaService, CategoriaService categoriaService, ProdutoService produtoService,
            EstoqueService estoqueService, ReservaService reservaService, AtendimentoService atendimentoService,
            ComandaService comandaService, PedidoService pedidoService, CaixaService caixaService,
            PagamentoService pagamentoService, AuditoriaService auditoria, ArmazenamentoDeImagens imagens,
            JdbcTemplate jdbc, EntityManager gerenciador, PlatformTransactionManager gerenteDeTransacao,
            @Value("${server.servlet.session.cookie.secure:false}") boolean producao) {
        this.funcionarios = funcionarios;
        this.produtos = produtos;
        this.codificador = codificador;
        this.mesaService = mesaService;
        this.categoriaService = categoriaService;
        this.produtoService = produtoService;
        this.estoqueService = estoqueService;
        this.reservaService = reservaService;
        this.atendimentoService = atendimentoService;
        this.comandaService = comandaService;
        this.pedidoService = pedidoService;
        this.caixaService = caixaService;
        this.pagamentoService = pagamentoService;
        this.auditoria = auditoria;
        this.imagens = imagens;
        this.jdbc = jdbc;
        this.gerenciador = gerenciador;
        this.transacao = new TransactionTemplate(gerenteDeTransacao);
        this.producao = producao;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        popular();
    }

    // Tudo numa transação só: se algo der errado, nada fica pela metade
    public void popular() {
        if (producao) {
            log.error("Modo demonstração recusado: o cookie seguro (RESTON_COOKIE_SEGURO=true) indica ambiente de produção.");
            return;
        }
        transacao.executeWithoutResult(status -> criarTudo());
    }

    private void criarTudo() {
        if (funcionarios.existsByLoginIgnoreCase(LOGIN_MARCADOR)) {
            log.info("Dados de demonstração já existem (funcionário '{}'). Nada foi criado de novo.", LOGIN_MARCADOR);
            return;
        }
        if (jdbc.queryForObject("select count(*) from mesa", Long.class) > 0
                || jdbc.queryForObject("select count(*) from produto", Long.class) > 0) {
            log.warn("Modo demonstração ignorado: o banco já tem mesas ou produtos. Use um banco vazio (docker compose down -v).");
            return;
        }
        chegadas.clear();
        idsDosProdutos.clear();

        Funcionario gerente = novoFuncionario("Helena Duarte", "gerente", Perfil.GERENTE);
        Funcionario garcom1 = novoFuncionario("Marcos Oliveira", "garcom1", Perfil.GARCOM);
        Funcionario garcom2 = novoFuncionario("Juliana Prado", "garcom2", Perfil.GARCOM);
        Funcionario caixa = novoFuncionario("Carlos Mendes", "caixa", Perfil.CAIXA);
        novoFuncionario("Rita Almeida", "cozinha", Perfil.COZINHA);
        novoFuncionario("Felipe Rocha", "bartender", Perfil.BARTENDER);
        FuncionarioDetalhes gestao = new FuncionarioDetalhes(gerente);
        FuncionarioDetalhes atendente1 = new FuncionarioDetalhes(garcom1);
        FuncionarioDetalhes atendente2 = new FuncionarioDetalhes(garcom2);
        FuncionarioDetalhes operador = new FuncionarioDetalhes(caixa);

        Map<Integer, Long> mesas = criarMesas(gestao);
        criarCardapio(gestao);
        configurarEstoque(gestao);

        gerenciador.flush();
        Map<String, Integer> pesos = Map.ofEntries(
                Map.entry("Cerveja Long Neck", 6), Map.entry("Batata Frita", 4), Map.entry("Caipirinha de Limão", 4),
                Map.entry("Refrigerante Lata", 3), Map.entry("Pastel de Queijo", 3), Map.entry("Filé à Parmegiana", 3),
                Map.entry("Água sem gás", 2), Map.entry("Moqueca de Peixe", 2), Map.entry("Risoto de Camarão", 2),
                Map.entry("Isca de Peixe", 2), Map.entry("Gin Tônica", 2), Map.entry("Mojito", 2),
                Map.entry("Brownie com Sorvete", 2), Map.entry("Suco Natural de Laranja", 2), Map.entry("Calabresa Acebolada", 2),
                Map.entry("Cerveja Artesanal IPA", 2));
        List<Long> todasAsMesas = new ArrayList<>(mesas.values());
        HistoricoDeVendas historico = new HistoricoDeVendas(jdbc, todasAsMesas, List.of(garcom1.getId(), garcom2.getId()),
                caixa.getId(), pesos);
        historico.gerarDiasAnteriores(DIAS_DE_HISTORICO, 4, new BigDecimal("-7.50"));

        // Caixa de hoje: aberto pelo serviço de verdade, só o horário de abertura volta para a manhã
        Instant agora = Instant.now();
        Long turno = caixaService.abrir(new BigDecimal("200.00"), operador).id();
        gerenciador.flush();
        Instant abertura = ZonedDateTime.now(FUSO).toLocalDate().atTime(11, 0).atZone(FUSO).toInstant();
        abertura = abertura.isBefore(agora.minus(1, ChronoUnit.HOURS)) ? abertura : agora.minus(1, ChronoUnit.HOURS);
        Instant inicioDoDia = LocalDate.now(FUSO).atStartOfDay(FUSO).toInstant();
        abertura = abertura.isBefore(inicioDoDia) ? inicioDoDia : abertura;
        jdbc.update("update caixa_turno set aberto_em = ? where id = ?", Timestamp.from(abertura), turno);
        historico.gerarHoje(turno, abertura, agora, List.of(mesas.get(1), mesas.get(3), mesas.get(5), mesas.get(6)));

        Long atendimento2 = criarMesaDoMarcos(mesas.get(2), atendente1, gestao);
        Long atendimento4 = criarMesaDaJuliana(mesas.get(4), atendente2, gestao);
        criarReservas(mesas, gestao, operador);

        gerenciador.flush();
        gerenciador.clear();
        ajustarHorarios(agora, atendimento2, atendimento4);
        jdbc.update("update mesa set estado = 'AGUARDANDO_LIBERACAO' where id = ?", mesas.get(3));

        auditoria.registrar(gerente.getId(), gerente.getLogin(), "DADOS_DEMO_CRIADOS", null, null, "modo demonstração");
        avisarNoLog();
    }

    private Funcionario novoFuncionario(String nome, String login, Perfil perfil) {
        PoliticaSenha.validar(SENHA_DEMO, login);
        return funcionarios.save(new Funcionario(nome, login, codificador.encode(SENHA_DEMO), perfil, false));
    }

    private Map<Integer, Long> criarMesas(FuncionarioDetalhes autor) {
        int[] lugares = { 2, 4, 4, 6, 6, 8 };
        Map<Integer, Long> ids = new HashMap<>();
        for (int i = 0; i < lugares.length; i++) {
            ids.put(i + 1, mesaService.criar(new MesaRequest(i + 1, lugares[i], true, i < 4 ? "Salão" : "Varanda"), autor).id());
        }
        return ids;
    }

    private void criarCardapio(FuncionarioDetalhes autor) {
        Map<String, Long> categorias = new HashMap<>();
        for (String nome : List.of("Pratos", "Porções", "Bebidas", "Drinques", "Sobremesas")) {
            categorias.put(nome, categoriaService.criar(new CategoriaRequest(nome), autor).id());
        }

        List<ItemDoCardapio> cardapio = List.of(
                new ItemDoCardapio("Moqueca de Peixe", "Pratos", "Peixe fresco no leite de coco e dendê, com arroz e pirão.", "79.90", 35, DestinoPreparo.COZINHA, "moqueca.jpg"),
                new ItemDoCardapio("Filé à Parmegiana", "Pratos", "Filé empanado com molho de tomate e queijo, arroz e batata frita.", "62.00", 25, DestinoPreparo.COZINHA, "file.jpg"),
                new ItemDoCardapio("Risoto de Camarão", "Pratos", "Arroz arbóreo cremoso com camarões e parmesão.", "74.90", 30, DestinoPreparo.COZINHA, "risoto.jpg"),
                new ItemDoCardapio("Salada Caesar com Frango", "Pratos", "Alface, frango grelhado, croutons e molho caesar.", "38.00", 12, DestinoPreparo.COZINHA, "salada.jpg"),
                new ItemDoCardapio("Pastel de Queijo", "Porções", "Porção com 6 pastéis crocantes recheados de queijo.", "32.00", 12, DestinoPreparo.COZINHA, "pastel.jpg"),
                new ItemDoCardapio("Batata Frita", "Porções", "Porção grande de batata frita com molho da casa.", "29.90", 15, DestinoPreparo.COZINHA, "batata.jpg"),
                new ItemDoCardapio("Isca de Peixe", "Porções", "Tiras de peixe empanadas com molho tártaro.", "49.90", 18, DestinoPreparo.COZINHA, "isca.jpg"),
                new ItemDoCardapio("Bolinho de Bacalhau", "Porções", "Porção com 8 bolinhos de bacalhau.", "44.90", 18, DestinoPreparo.COZINHA, "bolinho.jpg"),
                new ItemDoCardapio("Calabresa Acebolada", "Porções", "Calabresa na chapa com cebola e pão.", "36.00", 15, DestinoPreparo.COZINHA, "calabresa.jpg"),
                new ItemDoCardapio("Cerveja Long Neck", "Bebidas", "Long neck gelada 330 ml.", "12.00", 0, DestinoPreparo.SEM_PREPARO, "cerveja.jpg"),
                new ItemDoCardapio("Cerveja Artesanal IPA", "Bebidas", "Garrafa 500 ml de cerveja artesanal IPA.", "28.00", 0, DestinoPreparo.SEM_PREPARO, "ipa.jpg"),
                new ItemDoCardapio("Refrigerante Lata", "Bebidas", "Lata 350 ml (cola ou limão).", "7.00", 0, DestinoPreparo.SEM_PREPARO, "refri.jpg"),
                new ItemDoCardapio("Guaraná Lata", "Bebidas", "Lata 350 ml.", "7.00", 0, DestinoPreparo.SEM_PREPARO, "guarana.jpg"),
                new ItemDoCardapio("Água sem gás", "Bebidas", "Garrafa 500 ml.", "5.00", 0, DestinoPreparo.SEM_PREPARO, "agua.jpg"),
                new ItemDoCardapio("Água com gás", "Bebidas", "Garrafa 500 ml.", "6.00", 0, DestinoPreparo.SEM_PREPARO, "agua-com-gas.jpg"),
                new ItemDoCardapio("Suco Natural de Laranja", "Bebidas", "Copo de 400 ml, feito na hora.", "11.00", 5, DestinoPreparo.BAR, "suco.jpg"),
                new ItemDoCardapio("Caipirinha de Limão", "Drinques", "Cachaça, limão, açúcar e gelo.", "24.00", 6, DestinoPreparo.BAR, "caipirinha.jpg"),
                new ItemDoCardapio("Mojito", "Drinques", "Rum, hortelã, limão, açúcar e água com gás.", "28.00", 7, DestinoPreparo.BAR, "mojito.jpg"),
                new ItemDoCardapio("Gin Tônica", "Drinques", "Gin, água tônica, limão siciliano e especiarias.", "34.00", 5, DestinoPreparo.BAR, "gin.jpg"),
                new ItemDoCardapio("Aperol Spritz", "Drinques", "Aperol, espumante e água com gás, com laranja.", "36.00", 5, DestinoPreparo.BAR, "aperol.jpg"),
                new ItemDoCardapio("Pudim de Leite", "Sobremesas", "Fatia de pudim de leite condensado com calda.", "18.00", 3, DestinoPreparo.COZINHA, "pudim.jpg"),
                new ItemDoCardapio("Brownie com Sorvete", "Sobremesas", "Brownie quente com sorvete de creme.", "26.00", 8, DestinoPreparo.COZINHA, "brownie.jpg"));

        for (ItemDoCardapio item : cardapio) {
            ProdutoRequest dados = new ProdutoRequest(categorias.get(item.categoria()), item.nome(), item.descricao(),
                    new BigDecimal(item.preco()), item.preparo(), item.destino());
            Long id = produtoService.criar(dados, autor).id();
            idsDosProdutos.put(item.nome(), id);
            if (item.foto() != null) {
                colocarFoto(id, item.foto());
            }
        }
    }

    // As fotos vêm da pasta demo/fotos do projeto; se uma não for encontrada, o produto fica sem foto
    private void colocarFoto(Long produtoId, String arquivo) {
        try {
            byte[] bytes = new ClassPathResource("demo/fotos/" + arquivo).getContentAsByteArray();
            Produto produto = produtos.findById(produtoId).orElseThrow();
            produto.setImagem(imagens.salvar(bytes));
        } catch (IOException | RuntimeException erro) {
            log.warn("Não consegui colocar a foto {}: {}", arquivo, erro.getMessage());
        }
    }

    // Saldo e mínimo de cada bebida pronta: um baixo (IPA) e um esgotado (Guaraná)
    private void configurarEstoque(FuncionarioDetalhes autor) {
        controlar("Cerveja Long Neck", 12, 36, autor);
        controlar("Cerveja Artesanal IPA", 8, 4, autor);
        controlar("Refrigerante Lata", 10, 30, autor);
        controlar("Guaraná Lata", 10, 12, autor);
        controlar("Água sem gás", 15, 48, autor);
        controlar("Água com gás", 10, 14, autor);
        estoqueService.ajustar(idsDosProdutos.get("Guaraná Lata"), new AjusteRequest(0, "Contagem: acabou o estoque"), autor);
    }

    private void controlar(String produto, int minimo, int inicial, FuncionarioDetalhes autor) {
        estoqueService.configurar(idsDosProdutos.get(produto), new ControleRequest(true, minimo, inicial), autor);
    }

    // Mesa 2: duas comandas, com um pedido atrasado na cozinha e outros em várias etapas
    private Long criarMesaDoMarcos(Long mesaId, FuncionarioDetalhes garcom, FuncionarioDetalhes gestao) {
        Long atendimento = atendimentoService.abrir(mesaId, garcom).id();
        Long carla = comandaService.abrir(atendimento, "Carla", garcom).id();
        Long rafael = comandaService.abrir(atendimento, "Rafael", garcom).id();

        pedir(carla, garcom, gestao, StatusPedido.ENTREGUE, 60, item("Cerveja Long Neck", 2));
        pedir(carla, garcom, gestao, StatusPedido.EM_PREPARO, 42, item("Moqueca de Peixe", 1), item("Batata Frita", 1));
        pedir(carla, garcom, gestao, StatusPedido.PRONTO, 15, item("Caipirinha de Limão", 2));
        pedir(rafael, garcom, gestao, StatusPedido.ENTREGUE, 50, item("Refrigerante Lata", 1));
        pedir(rafael, garcom, gestao, StatusPedido.PRONTO, 24, item("Filé à Parmegiana", 1));
        pedir(rafael, garcom, gestao, StatusPedido.RECEBIDO, 3, item("Gin Tônica", 1));
        return atendimento;
    }

    // Mesa 4: uma comanda, pedidos recém-chegados
    private Long criarMesaDaJuliana(Long mesaId, FuncionarioDetalhes garcom, FuncionarioDetalhes gestao) {
        Long atendimento = atendimentoService.abrir(mesaId, garcom).id();
        Long familia = comandaService.abrir(atendimento, "Família Souza", garcom).id();

        pedir(familia, garcom, gestao, StatusPedido.RECEBIDO, 4, item("Pastel de Queijo", 1), item("Calabresa Acebolada", 1));
        pedir(familia, garcom, gestao, StatusPedido.EM_PREPARO, 9, item("Risoto de Camarão", 1));
        pedir(familia, garcom, gestao, StatusPedido.EM_PREPARO, 2, item("Suco Natural de Laranja", 2));
        pedir(familia, garcom, gestao, StatusPedido.PRONTO, 6, item("Água sem gás", 2));
        return atendimento;
    }

    private ItemRequest item(String produto, int quantidade) {
        return new ItemRequest(idsDosProdutos.get(produto), quantidade, null);
    }

    // Lança o pedido e avança o status até o desejado (o horário de chegada é acertado depois)
    private void pedir(Long comanda, FuncionarioDetalhes garcom, FuncionarioDetalhes gestao, StatusPedido alvo, int minutosAtras,
            ItemRequest... itens) {
        PedidoResposta pedido = pedidoService.criar(comanda, List.of(itens), garcom).get(0);
        StatusPedido atual = pedido.status();
        while (atual != alvo) {
            atual = atual.proximo();
            pedidoService.mudarStatus(pedido.id(), atual, gestao);
        }
        chegadas.add(new Chegada(pedido.id(), minutosAtras));
    }

    private void criarReservas(Map<Integer, Long> mesas, FuncionarioDetalhes gestao, FuncionarioDetalhes caixa) {
        ZonedDateTime proximaHora = ZonedDateTime.now(FUSO).truncatedTo(ChronoUnit.HOURS).plusHours(1);
        ZonedDateTime amanha = proximaHora.toLocalDate().plusDays(1).atStartOfDay(FUSO);

        reservar(mesas.get(5), "Fernanda Lima", "(11) 91234-5678", 4, proximaHora.plusHours(2), gestao);
        Long aniversario = reservar(mesas.get(6), "Bruno Tavares (aniversário)", "(11) 99876-5432", 7, proximaHora.plusHours(3), gestao);
        reservar(mesas.get(5), "Patrícia Souza", "(11) 98765-1234", 6, amanha.plusHours(12).plusMinutes(30), gestao);
        reservar(mesas.get(3), "Ricardo Alves", "(21) 97654-3210", 3, amanha.plusHours(20), gestao);
        reservar(mesas.get(4), "Empresa Horizonte", "(11) 93456-7890", 5, amanha.plusDays(1).plusHours(19).plusMinutes(30), gestao);
        reservar(mesas.get(1), "Camila Rocha", "(31) 98888-1111", 2, amanha.plusDays(2).plusHours(21), gestao);

        pagamentoService.antecipar(aniversario, MetodoPagamento.PIX, new BigDecimal("100.00"), caixa);
    }

    private Long reservar(Long mesaId, String nome, String telefone, int pessoas, ZonedDateTime inicio, FuncionarioDetalhes autor) {
        return reservaService.criar(new ReservaRequest(mesaId, nome, telefone, pessoas, inicio.toInstant()), autor).id();
    }

    // Coloca atendimentos, comandas e pedidos do "ao vivo" nos horários de alguns minutos atrás
    private void ajustarHorarios(Instant agora, Long atendimento2, Long atendimento4) {
        acertarAtendimento(atendimento2, agora.minus(65, ChronoUnit.MINUTES));
        acertarAtendimento(atendimento4, agora.minus(22, ChronoUnit.MINUTES));

        for (Chegada chegada : chegadas) {
            Instant criado = agora.minus(chegada.minutosAtras(), ChronoUnit.MINUTES);
            jdbc.update("update pedido set criado_em = ? where id = ?", Timestamp.from(criado), chegada.pedidoId());

            List<Long> passos = jdbc.queryForList("select id from pedido_historico where pedido_id = ? order by id", Long.class, chegada.pedidoId());
            for (int i = 0; i < passos.size(); i++) {
                Instant quando = criado.plus(i * 2L, ChronoUnit.MINUTES);
                jdbc.update("update pedido_historico set em = ? where id = ?", Timestamp.from(quando.isAfter(agora) ? agora : quando), passos.get(i));
            }
        }
    }

    private void acertarAtendimento(Long atendimento, Instant abertura) {
        jdbc.update("update atendimento set aberto_em = ? where id = ?", Timestamp.from(abertura), atendimento);
        jdbc.update("update comanda set aberta_em = ? where atendimento_id = ?", Timestamp.from(abertura.plus(1, ChronoUnit.MINUTES)), atendimento);
    }

    private void avisarNoLog() {
        log.warn("""

                ====== MODO DEMONSTRAÇÃO ======
                Dados de demonstração criados. Logins: admin (senha do primeiro acesso), gerente, garcom1,
                garcom2, caixa, cozinha e bartender.
                Senha dos funcionários de demonstração: {}
                Não use este modo em produção.
                ===============================""", SENHA_DEMO);
    }
}
