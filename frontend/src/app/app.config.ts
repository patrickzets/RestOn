import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { sessaoInterceptor } from './servicos/sessao.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // O Angular já manda o cabeçalho X-XSRF-TOKEN (proteção CSRF) nas chamadas que alteram dados
    provideHttpClient(withInterceptors([sessaoInterceptor])),
  ],
};
