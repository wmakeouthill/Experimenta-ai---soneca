import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { AuthorizationService, Role } from '../services/authorization.service';

/** O perfil TOTEM só enxerga o autoatendimento: qualquer outra rota o devolve para cá. */
const ROTA_TOTEM = '/autoatendimento';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.estaAutenticado()) {
    return authService.isTotem() ? router.parseUrl(ROTA_TOTEM) : true;
  }

  router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
  return false;
};

export const adminGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.estaAutenticado() && authService.isAdministrador()) {
    return true;
  }

  if (authService.isTotem()) {
    return router.parseUrl(ROTA_TOTEM);
  }

  router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
  return false;
};

export const roleGuard = (rolesPermitidos: Role[]): CanActivateFn => {
  return (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.estaAutenticado()) {
      router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
      return false;
    }

    if (authService.temAcessoA(rolesPermitidos)) {
      return true;
    }

    if (authService.isTotem()) {
      return router.parseUrl(ROTA_TOTEM);
    }

    router.navigate(['/'], { queryParams: { acessoNegado: true } });
    return false;
  };
};

export const operadorGuard: CanActivateFn = (route, state) => {
  return roleGuard(['ADMINISTRADOR', 'OPERADOR'])(route, state);
};

export const autoatendimentoGuard: CanActivateFn = (route, state) => {
  return roleGuard(['ADMINISTRADOR', 'OPERADOR', 'TOTEM'])(route, state);
};
