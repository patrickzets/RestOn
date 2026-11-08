package br.edu.reston.erro;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail campoInvalido(MethodArgumentNotValidException erro) {
        Map<String, String> campos = new LinkedHashMap<>();
        erro.getBindingResult().getFieldErrors().forEach(campo -> campos.putIfAbsent(campo.getField(), campo.getDefaultMessage()));

        ProblemDetail resposta = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Alguns campos estão inválidos.");
        resposta.setProperty("campos", campos);
        return resposta;
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ProblemDetail credenciaisInvalidas(CredenciaisInvalidasException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, erro.getMessage());
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ProblemDetail acessoNegado(AcessoNegadoException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, erro.getMessage());
    }

    // Lançada pelo @PreAuthorize quando o perfil não tem permissão
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail semPermissao(AccessDeniedException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Você não tem permissão para fazer isso.");
    }

    @ExceptionHandler(NaoEncontradoException.class)
    public ProblemDetail naoEncontrado(NaoEncontradoException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, erro.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail conflito(ConflitoException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, erro.getMessage());
    }

    // Duas pessoas cadastrando o mesmo login ao mesmo tempo: o banco recusa a segunda
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail registroRepetido(DataIntegrityViolationException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Já existe um registro com estes dados.");
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ProblemDetail regraQuebrada(RegraDeNegocioException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, erro.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail arquivoGrande(MaxUploadSizeExceededException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "A imagem deve ter no máximo 5 MB.");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail alteradoPorOutraPessoa(ObjectOptimisticLockingFailureException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Outra pessoa alterou este registro. Recarregue a tela e tente de novo.");
    }
}
