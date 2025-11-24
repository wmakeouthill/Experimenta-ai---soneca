import { Component, input, output, computed, ElementRef, ViewChild, effect, OnDestroy, PLATFORM_ID, inject, afterNextRender } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
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
export class OrderListComponent implements OnDestroy {
  private readonly platformId = inject(PLATFORM_ID);
  private resizeHandler?: () => void;
  private autoPaginaIniciada = false;

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

  private readonly pagination = usePagination(() => this.isModoGestor(), this.platformId);

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
        // Criar nova referência do array ao invés de mutar
        return [...lista, animandoDados];
      }
    }
    return lista;
  });

  readonly itensPaginados = computed(() => {
    // Incluir pagina atual no computed para forçar re-render quando mudar
    const _ = this.pagination.pagina();
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
    // Effects devem estar no contexto de injeção (constructor)
    // Effect para recalcular quando pedidos ou referência mudarem
    effect(() => {
      if (!isPlatformBrowser(this.platformId)) return;

      const pedidos = this.pedidosComAnimacao();

      if (this.listRef?.nativeElement && !this.isModoGestor()) {
        setTimeout(() => {
          this.pagination.calcularItensPorPagina(this.listRef);
          this.pagination.ajustarPagina(pedidos);
        }, 100);
      } else {
        this.pagination.pararAutoPagina();
      }
    });

    // Effect separado para iniciar/parar auto-paginação quando houver múltiplas páginas
    effect(() => {
      if (!isPlatformBrowser(this.platformId)) return;

      const pedidos = this.pedidosComAnimacao();
      const itensPorPagina = this.pagination.itensPorPagina();
      const info = this.pagination.getInfoPagina(pedidos);

      // Só inicia auto-paginação se:
      // 1. Não for modo gestor
      // 2. Tiver múltiplas páginas
      // 3. Tiver itensPorPagina calculado
      // 4. Tiver referência do elemento
      const deveTerAutoPagina = !this.isModoGestor() && info.temPagina && info.totalPaginas > 1 &&
        itensPorPagina && this.listRef?.nativeElement;

      if (deveTerAutoPagina) {
        // Verificar se já está rodando antes de tentar iniciar novamente
        if (!this.autoPaginaIniciada && !this.pagination.estaAutoPaginaRodando()) {
          this.autoPaginaIniciada = true;
          // Aguardar um pouco para garantir que o cálculo de itens por página foi feito
          // e que o ViewChild está disponível
          setTimeout(() => {
            if (this.listRef?.nativeElement && !this.pagination.estaAutoPaginaRodando()) {
              this.pagination.iniciarAutoPagina(() => this.pedidosComAnimacao());
            }
          }, 800);
        }
      } else if (this.autoPaginaIniciada || this.pagination.estaAutoPaginaRodando()) {
        // Se não atende mais as condições, parar e resetar flag
        this.autoPaginaIniciada = false;
        this.pagination.pararAutoPagina();
      }
    });

    // Aguardar hidratação para configurar event listeners e iniciar auto-paginação
    afterNextRender(() => {
      if (!isPlatformBrowser(this.platformId)) return;

      // Aguardar um pouco mais para garantir que ViewChild está disponível
      setTimeout(() => {
        // Verificar condições e iniciar auto-paginação se necessário
        const pedidos = this.pedidosComAnimacao();
        const itensPorPagina = this.pagination.itensPorPagina();
        const info = this.pagination.getInfoPagina(pedidos);

        if (!this.isModoGestor() && info.temPagina && info.totalPaginas > 1 &&
          itensPorPagina && this.listRef?.nativeElement && !this.pagination.estaAutoPaginaRodando()) {
          this.autoPaginaIniciada = true;
          this.pagination.iniciarAutoPagina(() => this.pedidosComAnimacao());
        }

        // Recalcular quando a janela redimensionar
        this.resizeHandler = () => {
          if (this.listRef?.nativeElement && !this.isModoGestor()) {
            setTimeout(() => {
              this.pagination.calcularItensPorPagina(this.listRef);
              this.pagination.ajustarPagina(this.pedidosComAnimacao());
            }, 100);
          }
        };

        window.addEventListener('resize', this.resizeHandler);
      }, 1000);
    });
  }

  ngOnDestroy() {
    if (isPlatformBrowser(this.platformId)) {
      if (this.resizeHandler) {
        window.removeEventListener('resize', this.resizeHandler);
      }
      this.autoPaginaIniciada = false;
      this.pagination.pararAutoPagina();
    }
  }

  handleMarcarComoPronto(id: string) {
    this.onMarcarComoPronto.emit(id);
  }

  handleRemover(id: string) {
    this.onRemover.emit(id);
  }
}

