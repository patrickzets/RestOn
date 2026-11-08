package br.edu.reston.funcionario;

import br.edu.reston.erro.RegraDeNegocioException;

public final class PoliticaSenha {

    public static final int TAMANHO_MINIMO = 10;

    private PoliticaSenha() {
    }

    public static void validar(String senha, String login) {
        if (senha == null || senha.length() < TAMANHO_MINIMO) {
            throw new RegraDeNegocioException("A senha precisa ter pelo menos " + TAMANHO_MINIMO + " caracteres.");
        }
        boolean temLetra = senha.chars().anyMatch(Character::isLetter);
        boolean temNumero = senha.chars().anyMatch(Character::isDigit);
        if (!temLetra || !temNumero) {
            throw new RegraDeNegocioException("A senha precisa ter letras e números.");
        }
        if (login != null && login.length() >= 3 && senha.toLowerCase().contains(login.toLowerCase())) {
            throw new RegraDeNegocioException("A senha não pode conter o seu login.");
        }
    }
}
