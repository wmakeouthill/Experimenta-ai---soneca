import { Component, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { Modulo } from '../../models/modulo.model';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent {
  private readonly modulos = signal<Modulo[]>([
    {
      id: 'cardapio',
      nome: 'Gestão de Cardápio',
      descricao: 'Gerenciar produtos, categorias e itens do cardápio',
      icone: '🍔',
      rota: '/cardapio',
      cor: 'primary',
      disponivel: true
    },
    {
      id: 'pedidos',
      nome: 'Gestão de Pedidos',
      descricao: 'Gerenciar pedidos, fila de preparo e status',
      icone: '📋',
      rota: '/pedidos',
      cor: 'success',
      disponivel: true
    }
  ]);

  readonly modulosDisponiveis = computed(() => 
    this.modulos().filter(m => m.disponivel)
  );

  constructor(private router: Router) {}

  navegarParaModulo(modulo: Modulo): void {
    if (modulo.disponivel) {
      this.router.navigate([modulo.rota]);
    }
  }
}

