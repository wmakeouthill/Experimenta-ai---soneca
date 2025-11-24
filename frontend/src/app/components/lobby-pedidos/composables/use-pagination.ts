import { signal, computed, effect, ElementRef } from '@angular/core';

interface PaginationInfo {
  totalPaginas: number;
  paginaAtual: number;
  temPagina: boolean;
}

export function usePagination(isModoGestor: () => boolean) {
  const pagina = signal(0);
  const itensPorPagina = signal<number | null>(null);

  const calcularItensPorPagina = (containerRef: ElementRef<HTMLElement> | null) => {
    if (!containerRef?.nativeElement) return;

    const container = containerRef.nativeElement;
    const alturaContainer = container.clientHeight;
    const primeiroItem = container.querySelector('.card-pedido') as HTMLElement;

    if (!primeiroItem || alturaContainer === 0) return;

    const alturaBase = 75;
    const gap = 15;
    const itens = Math.floor((alturaContainer - 20 + gap) / (alturaBase + gap));
    itensPorPagina.set(Math.max(1, itens));
  };

  const getItensPaginados = (items: any[]) => {
    if (isModoGestor() || !itensPorPagina() || items.length <= (itensPorPagina() || 0)) {
      return items;
    }
    const inicio = pagina() * (itensPorPagina() || 0);
    return items.slice(inicio, inicio + (itensPorPagina() || 0));
  };

  const getInfoPagina = (items: any[]): PaginationInfo => {
    if (isModoGestor() || !itensPorPagina() || items.length <= (itensPorPagina() || 0)) {
      return { totalPaginas: 1, paginaAtual: 0, temPagina: false };
    }
    const totalPaginas = Math.ceil(items.length / (itensPorPagina() || 0));
    return {
      totalPaginas,
      paginaAtual: pagina(),
      temPagina: totalPaginas > 1
    };
  };

  const ajustarPagina = (items: any[]) => {
    if (isModoGestor()) {
      pagina.set(0);
      return;
    }

    if (itensPorPagina() && items.length > (itensPorPagina() || 0)) {
      const totalPaginas = Math.ceil(items.length / (itensPorPagina() || 0));
      pagina.update(p => totalPaginas <= 1 ? 0 : Math.min(p, totalPaginas - 1));
    } else {
      pagina.set(0);
    }
  };

  return {
    pagina,
    itensPorPagina,
    calcularItensPorPagina,
    getItensPaginados,
    getInfoPagina,
    ajustarPagina
  };
}

