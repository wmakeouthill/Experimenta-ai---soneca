import { effect, afterNextRender, PLATFORM_ID, inject, ElementRef } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

interface PaginationApi {
  calcularItensPorPagina: (ref: ElementRef<HTMLElement>) => void;
  ajustarPagina: (pedidos: any[]) => void;
  iniciarAutoPagina: (getPedidos: () => any[]) => void;
  pararAutoPagina: () => void;
  estaAutoPaginaRodando: () => boolean;
  itensPorPagina: () => number | null;
  getInfoPagina: (pedidos: any[]) => { temPagina: boolean; totalPaginas: number };
}

interface OrderListEffectsConfig {
  platformId: Object;
  getColumnRef: () => ElementRef<HTMLElement> | null;
  isModoGestor: () => boolean;
  conteudoPausado: () => boolean;
  listaParaExibicao: () => any[];
  pagination: PaginationApi;
}

/**
 * Composable para gerenciar effects do OrderListComponent.
 * Responsabilidade única: configurar effects de paginação e auto-paginação.
 */
export function useOrderListEffects(config: OrderListEffectsConfig) {
  const platformId = inject(PLATFORM_ID);
  let autoPaginaIniciada = false;

  const recalcularPagina = () => {
    const pedidos = config.listaParaExibicao();
    const columnRef = config.getColumnRef();

    if (columnRef?.nativeElement && !config.isModoGestor()) {
      config.pagination.calcularItensPorPagina(columnRef);
      config.pagination.ajustarPagina(pedidos);
    }
  };

  const configurarEffectRecalculo = () => {
    effect(() => {
      if (!isPlatformBrowser(config.platformId)) return;

      const pedidos = config.listaParaExibicao();
      const pausado = config.conteudoPausado();
      const columnRef = config.getColumnRef();

      if (pausado) {
        config.pagination.pararAutoPagina();
        autoPaginaIniciada = false;
        return;
      }

      if (columnRef?.nativeElement && !config.isModoGestor()) {
        setTimeout(() => recalcularPagina(), 100);
      } else {
        config.pagination.pararAutoPagina();
      }

      void pedidos;
    });
  };

  const configurarEffectAutoPagina = () => {
    effect(() => {
      if (!isPlatformBrowser(config.platformId)) return;

      const pedidos = config.listaParaExibicao();
      const pausado = config.conteudoPausado();
      const itensPorPagina = config.pagination.itensPorPagina();
      const info = config.pagination.getInfoPagina(pedidos);
      const columnRef = config.getColumnRef();

      if (pausado) {
        autoPaginaIniciada = false;
        config.pagination.pararAutoPagina();
        return;
      }

      const deveTer = !config.isModoGestor() &&
                      info.temPagina &&
                      info.totalPaginas > 1 &&
                      itensPorPagina !== null &&
                      columnRef?.nativeElement !== null;

      if (deveTer) {
        if (!autoPaginaIniciada && !config.pagination.estaAutoPaginaRodando()) {
          autoPaginaIniciada = true;
          setTimeout(() => {
            if (!config.pagination.estaAutoPaginaRodando()) {
              config.pagination.iniciarAutoPagina(() => config.listaParaExibicao());
            }
          }, 800);
        }
      } else if (autoPaginaIniciada || config.pagination.estaAutoPaginaRodando()) {
        autoPaginaIniciada = false;
        config.pagination.pararAutoPagina();
      }
    });
  };

  const configurarAfterNextRender = () => {
    afterNextRender(() => {
      if (!isPlatformBrowser(config.platformId)) return;

      setTimeout(() => {
        if (config.conteudoPausado()) return;

        recalcularPagina();

        const pedidos = config.listaParaExibicao();
        const itensPorPagina = config.pagination.itensPorPagina();
        const info = config.pagination.getInfoPagina(pedidos);
        const columnRef = config.getColumnRef();

        if (!config.isModoGestor() &&
            info.temPagina &&
            info.totalPaginas > 1 &&
            itensPorPagina !== null &&
            columnRef?.nativeElement !== null &&
            !config.pagination.estaAutoPaginaRodando()) {
          autoPaginaIniciada = true;
          config.pagination.iniciarAutoPagina(() => config.listaParaExibicao());
        }
      }, 300);
    });
  };

  const limpar = () => {
    autoPaginaIniciada = false;
    config.pagination.pararAutoPagina();
  };

  return {
    configurarEffectRecalculo,
    configurarEffectAutoPagina,
    configurarAfterNextRender,
    limpar
  };
}
