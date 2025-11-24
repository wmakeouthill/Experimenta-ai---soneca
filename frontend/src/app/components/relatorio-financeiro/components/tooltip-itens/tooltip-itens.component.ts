import { ChangeDetectionStrategy, Component, input, signal, HostListener, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormatoUtil } from '../../../../utils/formato.util';
import { ItemPedido } from '../../../../services/pedido.service';

@Component({
  selector: 'app-tooltip-itens',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './tooltip-itens.component.html',
  styleUrl: './tooltip-itens.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TooltipItensComponent {
  readonly itens = input.required<ItemPedido[]>();
  readonly aberto = signal<boolean>(false);

  @ViewChild('tooltip', { static: false }) tooltipRef?: ElementRef<HTMLElement>;
  @ViewChild('link', { static: false }) linkRef?: ElementRef<HTMLElement>;

  formatarMoeda(valor: number): string {
    return FormatoUtil.moeda(valor);
  }

  toggleTooltip(event: Event): void {
    event.stopPropagation();
    const estavaAberto = this.aberto();
    this.aberto.update(aberto => !aberto);

    if (!estavaAberto) {
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          this.posicionarTooltip();
        });
      });
    }
  }

  posicionarTooltip(): void {
    if (!this.tooltipRef?.nativeElement || !this.linkRef?.nativeElement) return;

    const tooltip = this.tooltipRef.nativeElement;
    const link = this.linkRef.nativeElement;
    const linkRect = link.getBoundingClientRect();

    const DISTANCIA_VERTICAL = 6;
    const centroLink = linkRect.left + (linkRect.width / 2);

    tooltip.style.visibility = 'visible';
    tooltip.style.opacity = '0';
    tooltip.style.left = `${centroLink}px`;
    tooltip.style.top = '0px';

    const tooltipRect = tooltip.getBoundingClientRect();
    const top = linkRect.top - tooltipRect.height - DISTANCIA_VERTICAL;

    tooltip.style.top = `${top}px`;
    tooltip.style.opacity = '1';
  }

  @HostListener('document:click', ['$event'])
  fecharAoClicarFora(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    const container = target.closest('.tooltip-itens-container');

    if (!container && this.aberto()) {
      this.aberto.set(false);
    }
  }
}

