package br.edu.reston.auditoria;

import java.util.List;

public record PaginaResposta<T>(List<T> itens, long total, int pagina, int tamanho) {
}
