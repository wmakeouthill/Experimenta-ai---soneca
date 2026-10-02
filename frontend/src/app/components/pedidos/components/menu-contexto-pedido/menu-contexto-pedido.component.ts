import { CommonModule } from '@angular/common';
import {
  afterRender,
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { MeioPagamento, Pedido, Piso, StatusPedido } from '../../../../services/pedido.service';
import { IconeComponent } from '../../../shared/icone/icone.component';

@Component({
  selector: 'app-menu-contexto-pedido',
  standalone: true,
  imports: [CommonModule, IconeComponent],
  templateUrl: './menu-contexto-pedido.component.html',
  styleUrl: './menu-contexto-pedido.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MenuContextoPedidoComponent {
  private readonly menuRef = viewChild<ElementRef<HTMLElement>>('menu');
  private ajustadoPara: { x: number; y: number } | null = null;

  readonly aberto = input.required<boolean>();
  readonly posicao = input<{ x: number; y: number } | null>(null);
  readonly pedido = input<Pedido | null>(null);
  readonly esquerda = signal(0);
  readonly topo = signal(0);
  readonly alturaMaxima = signal<number | null>(null);
  readonly onFechar = output<void>();
  readonly onStatusAlterado = output<{ pedidoId: string; novoStatus: StatusPedido }>();
  readonly onCancelar = output<string>();
  readonly onImprimirSegundaVia = output<string>();
  readonly onCorrigirTroco = output<string>();
  readonly onPisoAlterado = output<{ pedidoId: string; piso: Piso }>();

  readonly pisos: readonly Piso[] = ['TERREO', 'ANDAR'];

  readonly StatusPedido = StatusPedido;

  constructor() {
    effect(() => {
      const aberto = this.aberto();
      const posicao = this.posicao();
      this.ajustadoPara = null;
      if (!aberto || !posicao || typeof window === 'undefined') {
        return;
      }
      const margem = 12;
      const limiteAltura = Math.max(160, window.innerHeight - margem * 2);
      const alturaEstimada = Math.min(480, limiteAltura);
      let y = posicao.y;
      if (y + alturaEstimada > window.innerHeight - margem) {
        y = Math.max(margem, posicao.y - alturaEstimada);
      }
      this.esquerda.set(posicao.x);
      this.topo.set(y);
      this.alturaMaxima.set(limiteAltura);
    });

    afterRender(() => {
      const posicao = this.posicao();
      if (!this.aberto() || !posicao || this.ajustadoPara === posicao) {
        return;
      }
      if (!this.menuRef()?.nativeElement) {
        return;
      }
      this.encaixarNaTela();
      this.ajustadoPara = posicao;
    });
  }

  private encaixarNaTela(): void {
    const menu = this.menuRef()?.nativeElement;
    const posicao = this.posicao();
    if (!menu || !posicao || !this.aberto()) {
      return;
    }

    const margem = 12;
    const limiteAltura = Math.max(160, window.innerHeight - margem * 2);
    const altura = Math.min(menu.scrollHeight, limiteAltura);
    const largura = menu.offsetWidth;

    let x = posicao.x;
    let y = posicao.y;
    if (y + altura > window.innerHeight - margem) {
      const acima = posicao.y - altura;
      y = acima >= margem ? acima : margem;
    }
    if (x + largura > window.innerWidth - margem) {
      x = Math.max(margem, window.innerWidth - margem - largura);
    }
    if (x < margem) {
      x = margem;
    }

    this.esquerda.set(x);
    this.topo.set(y);
    this.alturaMaxima.set(limiteAltura);
  }

  obterStatusDisponiveis(statusAtual: StatusPedido): StatusPedido[] {
    const todosStatus = [
      StatusPedido.PENDENTE,
      StatusPedido.PREPARANDO,
      StatusPedido.PRONTO,
      StatusPedido.FINALIZADO,
    ];
    // CANCELADO não pode ser alterado (regra de negócio)
    // FINALIZADO não pode ser alterado para outros status, mas pode ser cancelado
    if (statusAtual === StatusPedido.CANCELADO) {
      return [];
    }
    // FINALIZADO não pode ser alterado para outros status, mas pode ser cancelado
    if (statusAtual === StatusPedido.FINALIZADO) {
      return [];
    }
    return todosStatus.filter(s => s !== statusAtual);
  }

  podeCancelar(statusAtual: StatusPedido): boolean {
    // Permite cancelar pedidos finalizados para casos especiais
    return statusAtual !== StatusPedido.CANCELADO;
  }

  cancelarPedido(): void {
    const pedido = this.pedido();
    if (pedido) {
      this.onCancelar.emit(pedido.id);
    }
    this.fechar();
  }

  obterNomeStatus(status: StatusPedido): string {
    const nomes: Record<StatusPedido, string> = {
      [StatusPedido.PENDENTE]: 'Aguardando',
      [StatusPedido.PREPARANDO]: 'Preparando',
      [StatusPedido.PRONTO]: 'Pronto',
      [StatusPedido.FINALIZADO]: 'Finalizado',
      [StatusPedido.CANCELADO]: 'Cancelado',
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

  imprimirSegundaVia(): void {
    const pedido = this.pedido();
    if (pedido) {
      this.onImprimirSegundaVia.emit(pedido.id);
    }
    this.fechar();
  }

  /**
   * Verifica se o pedido possui pagamento em dinheiro e pode ter o troco corrigido.
   * Disponível para pedidos que não estejam cancelados.
   */
  podeCorrigirTroco(pedido: Pedido): boolean {
    if (pedido.status === StatusPedido.CANCELADO) return false;
    return !!pedido.meiosPagamento?.some(m => m.meioPagamento === MeioPagamento.DINHEIRO);
  }

  corrigirTroco(): void {
    const pedido = this.pedido();
    if (pedido) {
      this.onCorrigirTroco.emit(pedido.id);
    }
    this.fechar();
  }

  nomePiso(piso: Piso): string {
    switch (piso) {
      case 'TERREO':
        return 'Térreo';
      case 'ANDAR':
        return '1º Andar';
      default: {
        const nunca: never = piso;
        return nunca;
      }
    }
  }

  alterarPiso(piso: Piso): void {
    const pedido = this.pedido();
    if (!pedido || pedido.piso === piso) {
      return;
    }
    this.onPisoAlterado.emit({ pedidoId: pedido.id, piso });
    this.fechar();
  }
}
