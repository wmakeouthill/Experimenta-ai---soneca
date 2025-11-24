import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, withFetch } from '@angular/common/http';

import { routes } from './app.routes';
import { provideClientHydration } from '@angular/platform-browser';
import { authInterceptor } from './interceptors/auth.interceptor';
import { authErrorInterceptor } from './interceptors/auth-error.interceptor';
import { silent404Interceptor } from './interceptors/silent-404.interceptor';
import { silent500ConfigInterceptor } from './interceptors/silent-500-config.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes), 
    provideClientHydration(),
    provideHttpClient(
      withFetch(),
      withInterceptors([
        authInterceptor,           // Adiciona token JWT nas requisições
        authErrorInterceptor,      // Trata erros 401/403 e redireciona para login
        silent404Interceptor,      // Trata 404 silenciosamente para sessões
        silent500ConfigInterceptor // Trata 500 silenciosamente para config
      ])
    )
  ]
};
