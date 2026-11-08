package br.edu.reston.funcionario;

import java.security.SecureRandom;

public final class SenhaProvisoria {

    // Sem letras e números que se confundem (0 e O, 1 e l e I)
    private static final String LETRAS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String NUMEROS = "23456789";
    private static final SecureRandom SORTEIO = new SecureRandom();

    private SenhaProvisoria() {
    }

    public static String gerar() {
        StringBuilder senha = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            senha.append(LETRAS.charAt(SORTEIO.nextInt(LETRAS.length())));
        }
        // Garante pelo menos um número e depois embaralha as posições
        char[] caracteres = senha.toString().toCharArray();
        for (int i = 0; i < 3; i++) {
            caracteres[SORTEIO.nextInt(caracteres.length)] = NUMEROS.charAt(SORTEIO.nextInt(NUMEROS.length()));
        }
        return new String(caracteres);
    }
}
