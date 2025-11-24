import { Component, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { Modulo } from '../../models/modulo.model';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent {
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  readonly usuarioAtual = this.authService.usuarioAtual;
  readonly estaAutenticado = this.authService.estaAutenticado;
  readonly isAdministrador = this.authService.isAdministrador;

  readonly modulosDisponiveis = computed(() => {
    const modulos: Modulo[] = [
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
      },
      {
        id: 'lobby-pedidos',
        nome: 'Lobby de Pedidos',
        descricao: 'Visualizar fila de pedidos em tempo real (preparando/pronto)',
        icone: '🖥️',
        rota: '/lobby-pedidos',
        cor: 'secondary',
        disponivel: true
      },
      {
        id: 'sessoes',
        nome: 'Gestão de Sessões',
        descricao: 'Gerenciar sessões de trabalho, iniciar, pausar e finalizar',
        icone: '📅',
        rota: '/sessoes',
        cor: 'info',
        disponivel: true
      },
      {
        id: 'historico-sessoes',
        nome: 'Histórico de Sessões',
        descricao: 'Visualizar relatórios e histórico de pedidos por sessão',
        icone: '📊',
        rota: '/historico-sessoes',
        cor: 'warning',
        disponivel: true
      },
      {
        id: 'relatorios',
        nome: 'Relatórios e Insights',
        descricao: 'Dashboards de vendas por período, categoria, cliente e horário',
        icone: '📈',
        rota: '/relatorios',
        cor: 'purple',
        disponivel: true
      }
    ];

    if (this.isAdministrador()) {
      modulos.push({
        id: 'administracao',
        nome: 'Administração',
        descricao: 'Gerenciar usuários, senhas e contas do sistema',
        icone: '⚙️',
        rota: '/administracao',
        cor: 'warning',
        disponivel: true
      });
    }

    return modulos;
  });

  navegarParaModulo(modulo: Modulo): void {
    if (modulo.disponivel) {
      this.router.navigate([modulo.rota]);
    }
  }

  logout(): void {
    this.authService.logout();
  }
}

