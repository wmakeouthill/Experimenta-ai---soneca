import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Pedido } from '../../../../services/pedido.service';
import { FormatoUtil } from '../../../../utils/formato.util';

@Component({
  selector: 'app-lobby-hero-card',
  standalone: true,
  templateUrl: './lobby-hero-card.component.html',
  styleUrl: './lobby-hero-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LobbyHeroCardComponent {
  readonly pedido = input.required<Pedido>();

  readonly numeroExibicao = computed(() => this.formatarNumero(this.pedido().numeroPedido));

  readonly nomeCliente = computed(() =>
    FormatoUtil.limitarPalavras(this.pedido().clienteNome, 3).toUpperCase()
  );

  private formatarNumero(numeroPedido: string): string {
    const digits = numeroPedido.replace(/\D/g, '');
    if (!digits) return numeroPedido;
    return digits.length > 3 ? digits.slice(-3) : digits;
  }
}
