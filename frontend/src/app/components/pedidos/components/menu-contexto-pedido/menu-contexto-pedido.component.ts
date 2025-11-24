import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StatusPedido, Pedido } from '../../../../services/pedido.service';

@Component({
  selector: 'app-menu-contexto-pedido',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './menu-contexto-pedido.component.html',
  styleUrl: './menu-contexto-pedido.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MenuContextoPedidoComponent {
  readonly aberto = input.required<boolean>();
  readonly posicao = input<{ x: number; y: number } | null>(null);
  readonly pedido = input<Pedido | null>(null);
  readonly onFechar = output<void>();
  readonly onStatusAlterado = output<{ pedidoId: string; novoStatus: StatusPedido }>();

  readonly StatusPedido = StatusPedido;

  obterStatusDisponiveis(statusAtual: StatusPedido): StatusPedido[] {
    const todosStatus = [
      StatusPedido.PENDENTE,
      StatusPedido.PREPARANDO,
      StatusPedido.PRONTO,
      StatusPedido.FINALIZADO
    ];
    // CANCELADO não pode ser alterado (regra de negócio)
    // FINALIZADO e CANCELADO não podem ser alterados para outros status
    if (statusAtual === StatusPedido.CANCELADO || statusAtual === StatusPedido.FINALIZADO) {
      return [];
    }
    return todosStatus.filter(s => s !== statusAtual);
  }

  obterNomeStatus(status: StatusPedido): string {
    const nomes: Record<StatusPedido, string> = {
      [StatusPedido.PENDENTE]: 'Aguardando',
      [StatusPedido.PREPARANDO]: 'Preparando',
      [StatusPedido.PRONTO]: 'Pronto',
      [StatusPedido.FINALIZADO]: 'Finalizado',
      [StatusPedido.CANCELADO]: 'Cancelado'
    };
    return nomes[status] || status;
  }

  alterarStatus(novoStatus: StatusPedido): void {
    const pedido = this.pedido();
    if (pedido) {
      this.onStatusAlterado.emit({ pedidoId: pedido.id, novoStatus });
    }
    this.fechar();
  }

  fechar(): void {
    this.onFechar.emit();
  }
}

