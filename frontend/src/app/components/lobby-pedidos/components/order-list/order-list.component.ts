import { Component, input, output, computed, ElementRef, ViewChild, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Pedido, StatusPedido } from '../../../../services/pedido.service';
import { OrderCardComponent } from '../order-card/order-card.component';
import { usePagination } from '../../composables/use-pagination';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [CommonModule, OrderCardComponent],
  templateUrl: './order-list.component.html',
  styleUrl: './order-list.component.css'
})
export class OrderListComponent {
  readonly title = input.required<string>();
  readonly status = input.required<StatusPedido>();
  readonly pedidos = input.required<Pedido[]>();
  readonly isModoGestor = input<boolean>(false);
  readonly isAnimating = input<boolean>(false);
  readonly pedidoAnimando = input<string | null>(null);
  readonly pedidoAnimandoDados = input<Pedido | null>(null);
  readonly pedidoAnimandoStatus = input<StatusPedido | null>(null);
  readonly onMarcarComoPronto = output<string>();
  readonly onRemover = output<string>();

  @ViewChild('listRef', { static: false }) listRef!: ElementRef<HTMLElement>;

  private readonly pagination = usePagination(() => this.isModoGestor());

  readonly pedidosFiltrados = computed(() => {
    const lista = this.pedidos().filter(p => {
      if (this.pedidoAnimando() === p.id) return false;
      return p.status === this.status();
    });
    return lista;
  });

  readonly pedidosComAnimacao = computed(() => {
    const lista = [...this.pedidosFiltrados()];
    const animandoDados = this.pedidoAnimandoDados();
    const animandoStatus = this.pedidoAnimandoStatus();
    
    if (animandoDados && animandoStatus === this.status() && animandoDados.status === this.status()) {
      const jaExiste = lista.some(p => p.id === animandoDados.id);
      if (!jaExiste) {
        lista.push(animandoDados);
      }
    }
    return lista;
  });

  readonly itensPaginados = computed(() => {
    return this.pagination.getItensPaginados(this.pedidosComAnimacao());
  });

  readonly infoPagina = computed(() => {
    return this.pagination.getInfoPagina(this.pedidosComAnimacao());
  });

  readonly paginasArray = computed(() => {
    const total = this.infoPagina().totalPaginas;
    return Array.from({ length: total }, (_, i) => i);
  });

  readonly isPreparando = computed(() => this.status() === StatusPedido.PREPARANDO);
  readonly columnClass = computed(() => this.isPreparando() ? 'coluna-preparando' : 'coluna-pronto');
  readonly headerClass = computed(() => this.isPreparando() ? 'preparando' : 'pronto');
  readonly titleText = computed(() => this.isPreparando() ? '⏳ PREPARANDO' : '✅ PRONTO');
  readonly emptyText = computed(() => this.isPreparando() ? 'Nenhum pedido em preparação' : 'Nenhum pedido pronto');

  constructor() {
    effect(() => {
      if (this.listRef?.nativeElement && !this.isModoGestor()) {
        setTimeout(() => {
          this.pagination.calcularItensPorPagina(this.listRef);
          this.pagination.ajustarPagina(this.pedidosComAnimacao());
        }, 100);
      }
    });
  }

  handleMarcarComoPronto(id: string) {
    this.onMarcarComoPronto.emit(id);
  }

  handleRemover(id: string) {
    this.onRemover.emit(id);
  }
}

