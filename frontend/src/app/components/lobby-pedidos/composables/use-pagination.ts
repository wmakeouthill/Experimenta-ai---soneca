import { signal, ElementRef } from '@angular/core';

interface PaginationInfo {
  totalPaginas: number;
  paginaAtual: number;
  temPagina: boolean;
}

export function usePagination(isModoGestor: () => boolean) {
  const pagina = signal(0);
  const itensPorPagina = signal<number | null>(null);
  let autoPaginaInterval: any = null;

  const calcularItensPorPagina = (containerRef: ElementRef<HTMLElement> | null) => {
    if (!containerRef?.nativeElement) return;

    const container = containerRef.nativeElement;
    const alturaContainer = container.clientHeight;
    const primeiroItem = container.querySelector('.card-pedido') as HTMLElement;

    if (!primeiroItem || alturaContainer === 0) {
      // Se não tem item ainda, tenta calcular baseado na altura do container
      const alturaBase = 100; // altura aproximada de um card
      const gap = 15;
      const itens = Math.floor((alturaContainer + gap) / (alturaBase + gap));
      itensPorPagina.set(Math.max(1, itens));
      return;
    }

    const alturaCard = primeiroItem.offsetHeight || 100;
    const gap = 15;
    const padding = 10; // padding da lista
    const alturaDisponivel = alturaContainer - padding;
    const itens = Math.floor((alturaDisponivel + gap) / (alturaCard + gap));
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
      pararAutoPagina();
      return;
    }

    if (itensPorPagina() && items.length > (itensPorPagina() || 0)) {
      const totalPaginas = Math.ceil(items.length / (itensPorPagina() || 0));
      pagina.update(p => totalPaginas <= 1 ? 0 : Math.min(p, totalPaginas - 1));
    } else {
      pagina.set(0);
      pararAutoPagina();
    }
  };

  const avancarPagina = (items: any[]) => {
    if (isModoGestor()) return;

    const info = getInfoPagina(items);
    if (info.temPagina) {
      const proximaPagina = (info.paginaAtual + 1) % info.totalPaginas;
      pagina.set(proximaPagina);
    }
  };

  const iniciarAutoPagina = (items: () => any[]) => {
    pararAutoPagina();

    if (isModoGestor()) return;

    // Verificar se está no browser
    if (typeof window === 'undefined') return;

    autoPaginaInterval = setInterval(() => {
      const lista = items();
      const info = getInfoPagina(lista);
      if (info.temPagina) {
        avancarPagina(lista);
      } else {
        pararAutoPagina();
      }
    }, 5000); // 5 segundos
  };

  const pararAutoPagina = () => {
    if (autoPaginaInterval) {
      clearInterval(autoPaginaInterval);
      autoPaginaInterval = null;
    }
  };

  return {
    pagina,
    itensPorPagina,
    calcularItensPorPagina,
    getItensPaginados,
    getInfoPagina,
    ajustarPagina,
    iniciarAutoPagina,
    pararAutoPagina
  };
}

