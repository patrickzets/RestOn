import { HttpErrorResponse } from '@angular/common/http';

// Transforma o erro da API (ProblemDetail) numa frase para mostrar na tela
export function mensagemDoErro(erro: HttpErrorResponse): string {
  if (erro.status === 0) return 'Não consegui falar com o servidor.';
  const campos = erro.error?.campos as Record<string, string> | undefined;
  if (campos) return Object.values(campos).join(' ');
  return erro.error?.detail ?? 'Algo deu errado. Tente de novo.';
}
