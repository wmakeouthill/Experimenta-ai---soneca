import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./components/home/home.component').then(m => m.HomeComponent)
  },
  {
    path: 'cardapio',
    loadComponent: () => import('./components/cardapio/cardapio.component').then(m => m.CardapioComponent)
  },
  {
    path: '**',
    redirectTo: ''
  }
];
