package br.edu.reston.cardapio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import br.edu.reston.erro.NaoEncontradoException;
import br.edu.reston.erro.RegraDeNegocioException;

// Guarda as fotos dos produtos numa pasta do servidor. O nome do arquivo é sempre gerado aqui
// (nunca o que o usuário mandou) e o tipo é conferido pelo conteúdo, não pela extensão.
@Component
public class ArmazenamentoDeImagens {

    public record Imagem(byte[] bytes, MediaType tipo) {
    }

    private final Path pasta;

    public ArmazenamentoDeImagens(@Value("${reston.upload.diretorio}") String diretorio) {
        this.pasta = Path.of(diretorio).toAbsolutePath().normalize();
    }

    // Salva a foto e devolve o nome do arquivo gerado
    public String salvar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Escolha uma imagem.");
        }
        try {
            return salvar(arquivo.getBytes());
        } catch (IOException erro) {
            throw new IllegalStateException("Não consegui gravar a imagem.", erro);
        }
    }

    // Mesma coisa, para quem já tem os bytes (usado pelos dados de demonstração)
    public String salvar(byte[] bytes) {
        try {
            String nome = UUID.randomUUID() + "." + descobrirExtensao(bytes);
            Files.createDirectories(pasta);
            Files.write(pasta.resolve(nome), bytes);
            return nome;
        } catch (IOException erro) {
            throw new IllegalStateException("Não consegui gravar a imagem.", erro);
        }
    }

    public Imagem ler(String nome) {
        Path arquivo = caminhoSeguro(nome);
        try {
            if (!Files.exists(arquivo)) {
                throw new NaoEncontradoException("Imagem não encontrada.");
            }
            return new Imagem(Files.readAllBytes(arquivo), tipoPelaExtensao(nome));
        } catch (IOException erro) {
            throw new IllegalStateException("Não consegui ler a imagem.", erro);
        }
    }

    public void apagar(String nome) {
        if (nome == null) return;
        try {
            Files.deleteIfExists(caminhoSeguro(nome));
        } catch (IOException erro) {
            // Sobrar um arquivo não atrapalha o sistema, então só seguimos em frente
        }
    }

    private String descobrirExtensao(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        throw new RegraDeNegocioException("Use uma imagem JPG, PNG ou WebP.");
    }

    private MediaType tipoPelaExtensao(String nome) {
        if (nome.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (nome.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }

    // Garante que o nome aponta para dentro da pasta de uploads
    private Path caminhoSeguro(String nome) {
        Path arquivo = pasta.resolve(nome).normalize();
        if (!arquivo.startsWith(pasta)) {
            throw new NaoEncontradoException("Imagem não encontrada.");
        }
        return arquivo;
    }
}
