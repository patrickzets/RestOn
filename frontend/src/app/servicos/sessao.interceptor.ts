import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

// 401 logado: a sessão acabou, volta para o login. 403 TROCAR_SENHA: ainda está com a provisória.
export const sessaoInterceptor: HttpInterceptorFn = (requisicao, proximo) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return proximo(requisicao).pipe(
    catchError((erro: HttpErrorResponse) => {
      const ehDeAutenticacao = requisicao.url.includes('/api/v1/auth/login') || requisicao.url.includes('/api/v1/auth/me');

      if (erro.status === 401 && !ehDeAutenticacao && auth.usuario()) {
        auth.limpar();
        router.navigateByUrl('/login');
      } else if (erro.status === 403 && erro.error?.codigo === 'TROCAR_SENHA') {
        router.navigateByUrl('/trocar-senha');
      }
      return throwError(() => erro);
    }),
  );
};
