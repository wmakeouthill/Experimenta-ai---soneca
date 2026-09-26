import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { AuthorizationService } from '../../services/authorization.service';
import { Modulo } from '../../models/modulo.model';
import { FormatoUtil } from '../../utils/formato.util';
import { IconeComponent } from '../shared/icone/icone.component';

export function saudacaoPorHora(hora: number): string {
  if (hora >= 5 && hora < 12) return 'Bom dia';
  if (hora >= 12 && hora < 18) return 'Boa tarde';
  return 'Boa noite';
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent {
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly authorizationService = inject(AuthorizationService);

  readonly usuarioAtual = this.authService.usuarioAtual;
  readonly estaAutenticado = this.authService.estaAutenticado;

  // ponytail: calculados ao abrir a home; se ela ficar aberta de um período para outro, só atualizam na próxima visita
  private readonly agora = new Date();
  readonly saudacao = saudacaoPorHora(this.agora.getHours());
  readonly dataHoje = this.agora.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });

  readonly nomeFormatado = computed(() => {
    const nome = this.usuarioAtual()?.nome;
    return FormatoUtil.capitalizarNome(nome);
  });

  readonly primeiroNome = computed(() => this.nomeFormatado().split(' ')[0]);

  readonly modulosDisponiveis = computed(() => {
    const modulosBase: Omit<Modulo, 'rolesPermitidos' | 'bloqueado'>[] = [
      {
        id: 'pedidos',
        nome: 'Gestão de Pedidos',
        descricao: 'Gerenciar pedidos, fila de preparo e status',
        icone: 'prancheta',
        rota: '/pedidos',
        disponivel: true
      },
      {
        id: 'cardapio',
        nome: 'Gestão de Cardápio',
        descricao: 'Gerenciar produtos, categorias e itens do cardápio',
        icone: 'hamburguer',
        rota: '/cardapio',
        disponivel: true
      },
      {
        id: 'lobby-pedidos',
        nome: 'Lobby de Pedidos',
        descricao: 'Visualizar fila de pedidos em tempo real (preparando/pronto)',
        icone: 'monitor',
        rota: '/lobby-pedidos',
        disponivel: true
      },
      {
        id: 'sessoes',
        nome: 'Gestão de Sessões',
        descricao: 'Gerenciar sessões de trabalho, iniciar, pausar e finalizar',
        icone: 'calendario',
        rota: '/sessoes',
        disponivel: true
      },
      {
        id: 'gestao-caixa',
        nome: 'Gestão de Caixa',
        descricao: 'Controle financeiro de dinheiro por sessão de trabalho',
        icone: 'dinheiro',
        rota: '/gestao-caixa',
        disponivel: true
      },
      {
        id: 'relatorios',
        nome: 'Relatórios e Insights',
        descricao: 'Dashboards de vendas por período, categoria, cliente e horário',
        icone: 'grafico',
        rota: '/relatorios',
        disponivel: true
      },
      {
        id: 'gestao-estoque',
        nome: 'Gestão de Estoque',
        descricao: 'Controle de estoque e inventário de produtos',
        icone: 'caixa',
        rota: '/gestao-estoque',
        disponivel: true
      },
      {
        id: 'administracao',
        nome: 'Administração',
        descricao: 'Gerenciar usuários, senhas e contas do sistema',
        icone: 'ajustes',
        rota: '/administracao',
        disponivel: true
      }
    ];

    return modulosBase.map(modulo => {
      const podeAcessar = this.authorizationService.podeAcessarModulo(modulo.id);
      const rolesPermitidos = this.authorizationService.getRolesPermitidos(modulo.id);

      return {
        ...modulo,
        rolesPermitidos,
        bloqueado: !podeAcessar,
        disponivel: podeAcessar
      };
    });
  });

  navegarParaModulo(modulo: Modulo, event: MouseEvent): void {
    if (!modulo.disponivel || modulo.bloqueado) {
      event.preventDefault();
      event.stopPropagation();
      return;
    }

    const url = this.router.serializeUrl(this.router.createUrlTree([modulo.rota]));
    const urlCompleta = window.location.origin + url;

    // Ctrl+Clique ou Clique com a rodinha → Abrir em nova guia
    if (event.ctrlKey || event.metaKey || event.button === 1) {
      event.preventDefault();
      window.open(urlCompleta, '_blank');
      return;
    }

    // Clique normal → Navegar na mesma página
    this.router.navigate([modulo.rota]);
  }

  logout(): void {
    this.authService.logout();
  }
}
