package br.edu.reston.erro;

// A mensagem é sempre a mesma, seja qual for o motivo, para não ajudar quem tenta invadir
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("Login ou senha inválidos.");
    }
}
