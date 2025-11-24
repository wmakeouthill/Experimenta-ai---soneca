import { Routes } from '@angular/router';
import { authGuard, adminGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./components/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: '',
    loadComponent: () => import('./components/home/home.component').then(m => m.HomeComponent),
    canActivate: [authGuard]
  },
  {
    path: 'cardapio',
    loadComponent: () => import('./components/cardapio/cardapio.component').then(m => m.CardapioComponent),
    canActivate: [authGuard]
  },
  {
    path: 'pedidos',
    loadComponent: () => import('./components/pedidos/pedidos.component').then(m => m.PedidosComponent),
    canActivate: [authGuard]
  },
  {
    path: 'sessoes',
    loadComponent: () => import('./components/sessoes/sessoes.component').then(m => m.SessoesComponent),
    canActivate: [authGuard]
  },
  {
    path: 'historico-sessoes',
    loadComponent: () => import('./components/historico-sessoes/historico-sessoes.component').then(m => m.HistoricoSessoesComponent),
    canActivate: [authGuard]
  },
  {
    path: 'relatorios',
    loadComponent: () => import('./components/relatorios/relatorios.component').then(m => m.RelatoriosComponent),
    canActivate: [authGuard]
  },
  {
    path: 'relatorio-financeiro',
    loadComponent: () => import('./components/relatorio-financeiro/relatorio-financeiro.component').then(m => m.RelatorioFinanceiroComponent),
    canActivate: [authGuard]
  },
  {
    path: 'administracao',
    loadComponent: () => import('./components/administracao/administracao.component').then(m => m.AdministracaoComponent),
    canActivate: [adminGuard]
  },
  {
    path: 'lobby-pedidos',
    loadComponent: () => import('./components/lobby-pedidos/lobby-pedidos.component').then(m => m.LobbyPedidosComponent)
  },
  {
    path: '**',
    redirectTo: ''
  }
];
