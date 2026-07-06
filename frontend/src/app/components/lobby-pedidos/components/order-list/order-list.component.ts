import { Component, input, output, computed, ElementRef, ViewChild, OnDestroy, PLATFORM_ID, inject, afterNextRender } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Pedido, StatusPedido } from '../../../../services/pedido.service';
import { OrderCardComponent } from '../order-card/order-card.component';
import { LobbyHeroCardComponent } from '../lobby-hero-card/lobby-hero-card.component';
import { usePagination } from '../../composables/use-pagination';
import { useResizeHandler } from '../../composables/use-resize-handler';
import { useOrderListEffects } from '../../composables/use-order-list-effects';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [CommonModule, OrderCardComponent, LobbyHeroCardComponent],
  templateUrl: './order-list.component.html',
  styleUrl: './order-list.component.css'
})
export class OrderListComponent implements OnDestroy {
  private readonly platformId = inject(PLATFORM_ID);

  readonly title = input.required<string>();
  readonly status = input.required<StatusPedido>();
  readonly pedidos = input.required<Pedido[]>();
  readonly isModoGestor = input<boolean>(false);
  readonly conteudoPausado = input<boolean>(false);
  readonly isAnimating = input<boolean>(false);
  readonly pedidoAnimando = input<string | null>(null);
  readonly pedidoAnimandoDados = input<Pedido | null>(null);
  readonly pedidoAnimandoStatus = input<StatusPedido | null>(null);
  readonly relogioMs = input<number>(Date.now());
  readonly onMarcarComoPronto = output<string>();
  readonly onRemover = output<string>();

  @ViewChild('columnRef', { static: false }) columnRef!: ElementRef<HTMLElement>;

  private readonly pagination = usePagination(() => this.isModoGestor(), this.platformId);
  private readonly resizeHandler = useResizeHandler({
    getListRef: () => this.columnRef,
    isModoGestor: () => this.isModoGestor(),
    onResize: () => this.handleResize()
  });
  private readonly effects = useOrderListEffects({
    platformId: this.platformId,
    getColumnRef: () => this.columnRef,
    isModoGestor: () => this.isModoGestor(),
    conteudoPausado: () => this.conteudoPausado(),
    listaParaExibicao: () => this.listaParaExibicao(),
    pagination: this.pagination,
  });

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
      const jaExiste = lista.some((p) => p.id === animandoDados.id);
      if (!jaExiste) {
        return [...lista, animandoDados];
      }
    }
    return lista;
  });

  readonly pedidoHero = computed(() => {
    if (this.isModoGestor() || !this.isPronto()) return null;
    const lista = this.pedidosComAnimacao();
    return lista.length > 0 ? lista[0] : null;
  });

  readonly listaParaExibicao = computed(() => {
    const hero = this.pedidoHero();
    if (!hero) return this.pedidosComAnimacao();
    return this.pedidosComAnimacao().filter((p) => p.id !== hero.id);
  });

  readonly itensPaginados = computed(() => {
    const _ = this.pagination.pagina();
    return this.pagination.getItensPaginados(this.listaParaExibicao());
  });

  readonly infoPagina = computed(() => this.pagination.getInfoPagina(this.listaParaExibicao()));

  readonly paginasArray = computed(() => {
    const total = this.infoPagina().totalPaginas;
    return Array.from({ length: total }, (_, i) => i);
  });

  readonly isPreparando = computed(() => this.status() === StatusPedido.PREPARANDO);
  readonly isPronto = computed(() => this.status() === StatusPedido.PRONTO);
  readonly columnClass = computed(() => (this.isPreparando() ? 'coluna-preparando' : 'coluna-pronto'));
  readonly headerClass = computed(() => this.isPreparando() ? 'preparando' : 'pronto');
  readonly titleText = computed(() =>
    this.isPreparando() ? 'PREPARANDO' : 'PRONTO • PODE RETIRAR'
  );
  readonly emptyText = computed(() =>
    this.isPreparando() ? 'Nenhum pedido em preparo' : 'Nenhum pedido pronto'
  );

  constructor() {
    this.effects.configurarEffectRecalculo();
    this.effects.configurarEffectAutoPagina();
    this.effects.configurarAfterNextRender();

    afterNextRender(() => {
      setTimeout(() => {
        this.resizeHandler.configurar();
      }, 1000);
    });
  }

  private handleResize(): void {
    this.pagination.calcularItensPorPagina(this.columnRef);
    this.pagination.ajustarPagina(this.listaParaExibicao());
  }

  ngOnDestroy(): void {
    this.resizeHandler.limpar();
    this.effects.limpar();
  }

  handleMarcarComoPronto(id: string) {
    this.onMarcarComoPronto.emit(id);
  }

  handleRemover(id: string) {
    this.onRemover.emit(id);
  }
}

