package br.edu.reston.funcionario;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.reston.auditoria.AuditoriaService;
import br.edu.reston.auth.FuncionarioDetalhes;
import br.edu.reston.erro.AcessoNegadoException;
import br.edu.reston.erro.ConflitoException;
import br.edu.reston.erro.NaoEncontradoException;
import br.edu.reston.erro.RegraDeNegocioException;
import br.edu.reston.funcionario.dto.AtualizarFuncionarioRequest;
import br.edu.reston.funcionario.dto.FuncionarioCriadoResposta;
import br.edu.reston.funcionario.dto.FuncionarioResposta;
import br.edu.reston.funcionario.dto.NovoFuncionarioRequest;
import br.edu.reston.funcionario.dto.SenhaProvisoriaResposta;

@Service
public class FuncionarioService {

    private static final int MAXIMO_DE_TENTATIVAS = 5;

    private final FuncionarioRepository repositorio;
    private final PasswordEncoder codificador;
    private final AuditoriaService auditoria;

    public FuncionarioService(FuncionarioRepository repositorio, PasswordEncoder codificador, AuditoriaService auditoria) {
        this.repositorio = repositorio;
        this.codificador = codificador;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<FuncionarioResposta> listar() {
        return repositorio.findAllByOrderByNomeAsc().stream().map(FuncionarioResposta::de).toList();
    }


    @Transactional
    public FuncionarioCriadoResposta criar(NovoFuncionarioRequest dados, FuncionarioDetalhes autor) {
        garantirQuePodeAtribuirPerfil(dados.perfil(), autor);

        String login = dados.login().trim().toLowerCase();
        if (repositorio.existsByLoginIgnoreCase(login)) {
            throw new ConflitoException("Já existe um funcionário com este login.");
        }

        String senha = SenhaProvisoria.gerar();
        Funcionario novo = repositorio.save(
                new Funcionario(dados.nome().trim(), login, codificador.encode(senha), dados.perfil(), true));

        registrar(autor, "FUNCIONARIO_CRIADO", novo, "login=" + login + ", perfil=" + dados.perfil());
        return new FuncionarioCriadoResposta(FuncionarioResposta.de(novo), senha);
    }

    @Transactional
    public FuncionarioResposta atualizar(Long id, AtualizarFuncionarioRequest dados, FuncionarioDetalhes autor) {
        Funcionario alvo = buscar(id);
        garantirQuePodeGerenciar(alvo, autor);
        garantirQuePodeAtribuirPerfil(dados.perfil(), autor);

        if (alvo.getId().equals(autor.getId()) && alvo.getPerfil() != dados.perfil()) {
            throw new RegraDeNegocioException("Você não pode mudar o seu próprio perfil.");
        }
        if (alvo.getPerfil() == Perfil.ADMIN && dados.perfil() != Perfil.ADMIN) {
            garantirQueSobraOutroAdministrador(alvo);
        }

        String antes = "perfil=" + alvo.getPerfil();
        alvo.setNome(dados.nome().trim());
        alvo.setPerfil(dados.perfil());

        registrar(autor, "FUNCIONARIO_ALTERADO", alvo, antes + " -> perfil=" + alvo.getPerfil());
        return FuncionarioResposta.de(alvo);
    }


    @Transactional
    public FuncionarioResposta bloquear(Long id, FuncionarioDetalhes autor) {
        Funcionario alvo = buscarParaAcao(id, autor, "bloquear");
        garantirQueSobraOutroAdministrador(alvo);
        alvo.setBloqueado(true);
        registrar(autor, "FUNCIONARIO_BLOQUEADO", alvo, null);
        return FuncionarioResposta.de(alvo);
    }

    @Transactional
    public FuncionarioResposta desbloquear(Long id, FuncionarioDetalhes autor) {
        Funcionario alvo = buscarParaAcao(id, autor, "desbloquear");
        alvo.setBloqueado(false);
        alvo.setTentativasFalha(0);
        registrar(autor, "FUNCIONARIO_DESBLOQUEADO", alvo, null);
        return FuncionarioResposta.de(alvo);
    }

    @Transactional
    public FuncionarioResposta desativar(Long id, FuncionarioDetalhes autor) {
        Funcionario alvo = buscarParaAcao(id, autor, "desativar");
        garantirQueSobraOutroAdministrador(alvo);
        alvo.setAtivo(false);
        registrar(autor, "FUNCIONARIO_DESATIVADO", alvo, null);
        return FuncionarioResposta.de(alvo);
    }

    @Transactional
    public FuncionarioResposta ativar(Long id, FuncionarioDetalhes autor) {
        Funcionario alvo = buscarParaAcao(id, autor, "ativar");
        alvo.setAtivo(true);
        registrar(autor, "FUNCIONARIO_ATIVADO", alvo, null);
        return FuncionarioResposta.de(alvo);
    }


    @Transactional
    public SenhaProvisoriaResposta redefinirSenha(Long id, FuncionarioDetalhes autor) {
        Funcionario alvo = buscarParaAcao(id, autor, "redefinir a senha de");
        String senha = SenhaProvisoria.gerar();
        alvo.setSenhaHash(codificador.encode(senha));
        alvo.setSenhaProvisoria(true);
        alvo.setTentativasFalha(0);
        registrar(autor, "SENHA_REDEFINIDA", alvo, null);
        return new SenhaProvisoriaResposta(senha);
    }

    @Transactional
    public void trocarPropriaSenha(Long id, String senhaAtual, String novaSenha) {
        Funcionario f = buscar(id);
        if (!codificador.matches(senhaAtual, f.getSenhaHash())) {
            throw new RegraDeNegocioException("A senha atual está errada.");
        }
        PoliticaSenha.validar(novaSenha, f.getLogin());
        if (codificador.matches(novaSenha, f.getSenhaHash())) {
            throw new RegraDeNegocioException("A nova senha precisa ser diferente da atual.");
        }
        f.setSenhaHash(codificador.encode(novaSenha));
        f.setSenhaProvisoria(false);
        auditoria.registrar(f.getId(), f.getLogin(), "SENHA_ALTERADA", "funcionario", String.valueOf(f.getId()), null);
    }


    @Transactional
    public void registrarAcessoOk(Long id) {
        Funcionario f = buscar(id);
        f.setUltimoAcessoEm(Instant.now());
        f.setTentativasFalha(0);
    }

    @Transactional
    public void registrarFalhaDeLogin(String login) {
        Optional<Funcionario> achado = repositorio.findByLoginIgnoreCase(login);
        if (achado.isEmpty()) {
            auditoria.registrar(null, login, "LOGIN_FALHA", "funcionario", null, "login inexistente");
            return;
        }

        Funcionario f = achado.get();
        f.setTentativasFalha(f.getTentativasFalha() + 1);
        auditoria.registrar(f.getId(), f.getLogin(), "LOGIN_FALHA", "funcionario", String.valueOf(f.getId()),
                "tentativa " + f.getTentativasFalha() + " de " + MAXIMO_DE_TENTATIVAS);

        if (f.getTentativasFalha() >= MAXIMO_DE_TENTATIVAS) {
            f.setBloqueado(true);
            auditoria.registrar(f.getId(), f.getLogin(), "CONTA_BLOQUEADA", "funcionario", String.valueOf(f.getId()),
                    MAXIMO_DE_TENTATIVAS + " senhas erradas seguidas");
        }
    }


    private Funcionario buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new NaoEncontradoException("Funcionário não encontrado."));
    }

    private Funcionario buscarParaAcao(Long id, FuncionarioDetalhes autor, String verbo) {
        Funcionario alvo = buscar(id);
        garantirQuePodeGerenciar(alvo, autor);
        if (alvo.getId().equals(autor.getId())) {
            throw new RegraDeNegocioException("Você não pode " + verbo + " a sua própria conta.");
        }
        return alvo;
    }

    private void garantirQuePodeGerenciar(Funcionario alvo, FuncionarioDetalhes autor) {
        if (autor.getPerfil() == Perfil.GERENTE && alvo.getPerfil() == Perfil.ADMIN) {
            throw new AcessoNegadoException("O Gerente não pode alterar a conta de um Administrador.");
        }
    }

    private void garantirQuePodeAtribuirPerfil(Perfil perfil, FuncionarioDetalhes autor) {
        if (autor.getPerfil() == Perfil.GERENTE && perfil == Perfil.ADMIN) {
            throw new AcessoNegadoException("Só um Administrador pode criar outro Administrador.");
        }
    }

    private void garantirQueSobraOutroAdministrador(Funcionario alvo) {
        boolean alvoAindaEntra = alvo.isAtivo() && !alvo.isBloqueado();
        if (alvo.getPerfil() == Perfil.ADMIN && alvoAindaEntra
                && repositorio.countByPerfilAndAtivoTrueAndBloqueadoFalse(Perfil.ADMIN) <= 1) {
            throw new RegraDeNegocioException("Precisa existir pelo menos um administrador ativo.");
        }
    }

    private void registrar(FuncionarioDetalhes autor, String acao, Funcionario alvo, String detalhes) {
        auditoria.registrar(autor.getId(), autor.getUsername(), acao, "funcionario", String.valueOf(alvo.getId()), detalhes);
    }
}
