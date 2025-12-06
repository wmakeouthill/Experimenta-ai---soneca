import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, withFetch } from '@angular/common/http';

import { routes } from './app.routes';
import { provideClientHydration, withNoHttpTransferCache } from '@angular/platform-browser';
import { authInterceptor } from './interceptors/auth.interceptor';
import { authErrorInterceptor } from './interceptors/auth-error.interceptor';
import { silent404Interceptor } from './interceptors/silent-404.interceptor';
import { silent500ConfigInterceptor } from './interceptors/silent-500-config.interceptor';
import { clienteAuthInterceptor } from './interceptors/cliente-auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    // withNoHttpTransferCache evita o warning NG0506 de hydration timeout
    // causado por operações assíncronas como polling, intervals e effects
    provideClientHydration(withNoHttpTransferCache()),
    provideHttpClient(
      withFetch(),
      withInterceptors([
        clienteAuthInterceptor,    // Adiciona token JWT e X-Cliente-Id para clientes
        authInterceptor,           // Adiciona token JWT nas requisições (funcionários)
        authErrorInterceptor,      // Trata erros 401/403 e redireciona para login
        silent404Interceptor,      // Trata 404 silenciosamente para sessões
        silent500ConfigInterceptor // Trata 500 silenciosamente para config
      ])
    )
  ]
};
