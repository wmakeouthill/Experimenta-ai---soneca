import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Interceptor para tratar erros de autenticação (401, 403).
 * Redireciona para login quando o token é inválido ou expirado.
 */
export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authService = inject(AuthService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Tratar apenas erros de autenticação/autorização
      if (error.status === 401 || error.status === 403) {
        // Limpar dados de autenticação
        authService.logout();
        
        // Redirecionar para login apenas se não estiver já na página de login
        // Usar window.location para evitar problemas de injeção circular
        if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
          const returnUrl = router.url;
          router.navigate(['/login'], {
            queryParams: { 
              returnUrl: returnUrl,
              reason: error.status === 401 ? 'unauthorized' : 'forbidden'
            }
          });
        }
      }
      
      // Propagar o erro para tratamento específico nos componentes
      return throwError(() => error);
    })
  );
};

