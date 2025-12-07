import { ApplicationConfig, APP_INITIALIZER } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, withFetch } from '@angular/common/http';

import { routes } from './app.routes';
import { provideClientHydration, withNoHttpTransferCache } from '@angular/platform-browser';
import { authInterceptor } from './interceptors/auth.interceptor';
import { authErrorInterceptor } from './interceptors/auth-error.interceptor';
import { silent404Interceptor } from './interceptors/silent-404.interceptor';
import { silent500ConfigInterceptor } from './interceptors/silent-500-config.interceptor';
import { clienteAuthInterceptor } from './interceptors/cliente-auth.interceptor';
import { clienteAuthErrorInterceptor } from './interceptors/cliente-auth-error.interceptor';
import { PwaInstallService } from './services/pwa-install.service';

// Instancia o serviço de PWA o mais cedo possível para não perder o evento beforeinstallprompt.
const initPwaInstallService = (service: PwaInstallService) => () => {
  // Apenas injetar já registra o listener; nenhuma ação extra aqui.
  return void service;
};

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
        clienteAuthErrorInterceptor, // Limpa sessão se o cliente não existir mais
        authInterceptor,           // Adiciona token JWT nas requisições (funcionários)
        authErrorInterceptor,      // Trata erros 401/403 e redireciona para login
        silent404Interceptor,      // Trata 404 silenciosamente para sessões
        silent500ConfigInterceptor // Trata 500 silenciosamente para config
      ])
    ),
    {
      provide: APP_INITIALIZER,
      useFactory: initPwaInstallService,
      deps: [PwaInstallService],
      multi: true
    }
  ]
};
