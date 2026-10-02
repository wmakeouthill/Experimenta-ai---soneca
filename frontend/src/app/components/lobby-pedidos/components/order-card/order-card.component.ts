import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Pedido, StatusPedido } from '../../../../services/pedido.service';
import { FormatoUtil } from '../../../../utils/formato.util';

@Component({
  selector: 'app-order-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './order-card.component.html',
  styleUrl: './order-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderCardComponent {
  readonly pedido = input.required<Pedido>();
  readonly isModoGestor = input<boolean>(false);
  readonly isAnimating = input<boolean>(false);
  readonly pedidoAnimando = input<string | null>(null);
  readonly relogioMs = input<number>(Date.now());
  readonly onMarcarComoPronto = output<string>();
  readonly onRemover = output<string>();

  readonly isPreparando = computed(() => this.pedido().status === StatusPedido.PREPARANDO);
  readonly isPronto = computed(() => this.pedido().status === StatusPedido.PRONTO);

  readonly nomeClienteLimitado = computed(() =>
    FormatoUtil.limitarPalavras(this.pedido().clienteNome, 3).toUpperCase()
  );

  readonly numeroExibicao = computed(() => {
    const daSessao = this.pedido().numeroExibicao;
    return daSessao ? daSessao : this.formatarNumero(this.pedido().numeroPedido);
  });

  readonly minutosDecorridos = computed(() => {
    const referencia = this.pedido().createdAt || this.pedido().dataPedido;
    const created = new Date(referencia);
    if (Number.isNaN(created.getTime())) return 0;
    return Math.max(0, Math.floor((this.relogioMs() - created.getTime()) / 60000));
  });

  readonly cardClass = computed(() => {
    const classes = ['card-pedido'];
    if (this.isPreparando()) classes.push('preparando');
    if (this.isPronto()) classes.push('pronto');
    if (!this.isModoGestor()) classes.push('modo-visualizacao');
    if (this.pedidoAnimando() === this.pedido().id) {
      classes.push(this.isPreparando() ? 'animando-saida' : 'animando-entrada');
    }
    return classes.join(' ');
  });

  handleMarcarComoPronto(): void {
    this.onMarcarComoPronto.emit(this.pedido().id);
  }

  handleRemover(): void {
    this.onRemover.emit(this.pedido().id);
  }

  private formatarNumero(numeroPedido: string): string {
    const digits = numeroPedido.replace(/\D/g, '');
    if (!digits) return numeroPedido;
    return digits.length > 3 ? digits.slice(-3) : digits;
  }
}
