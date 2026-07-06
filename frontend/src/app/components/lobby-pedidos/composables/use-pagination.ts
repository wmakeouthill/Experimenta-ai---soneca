import { signal, ElementRef } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

interface PaginationInfo {
  totalPaginas: number;
  paginaAtual: number;
  temPagina: boolean;
}

/** Altura fixa do card compacto no lobby TV (não medir DOM paginado — evita ciclo 1/página). */
const ALTURA_CARD_TV = 74;
const GAP_LISTA = 10;
const CAPACIDADE_MINIMA_TV = 5;
const PADDING_LISTA = 36;

export function usePagination(isModoGestor: () => boolean, platformId: Object) {
  const pagina = signal(0);
  const itensPorPagina = signal<number | null>(null);
  let autoPaginaInterval: ReturnType<typeof setInterval> | null = null;

  const calcularItensPorPagina = (columnRef: ElementRef<HTMLElement> | null) => {
    if (isModoGestor() || !columnRef?.nativeElement) return;

    const column = columnRef.nativeElement;
    if (column.clientHeight <= 0) return;

    const header = column.querySelector('.cabecalho-coluna') as HTMLElement | null;
    const hero = column.querySelector('.lobby-hero') as HTMLElement | null;
    const headerHeight = header?.offsetHeight ?? 0;
    const heroHeight = hero ? hero.offsetHeight + 12 : 0;
    const alturaDisponivel = column.clientHeight - headerHeight - heroHeight - PADDING_LISTA;

    if (alturaDisponivel <= 0) return;

    const itens = Math.floor((alturaDisponivel + GAP_LISTA) / (ALTURA_CARD_TV + GAP_LISTA));
    itensPorPagina.set(Math.max(CAPACIDADE_MINIMA_TV, itens));
  };

  const getItensPaginados = (items: any[]) => {
    if (isModoGestor()) return items;

    const capacidade = itensPorPagina();
    if (!capacidade || items.length <= capacidade) {
      return items;
    }

    const inicio = pagina() * capacidade;
    return items.slice(inicio, inicio + capacidade);
  };

  const getInfoPagina = (items: any[]): PaginationInfo => {
    if (isModoGestor()) {
      return { totalPaginas: 1, paginaAtual: 0, temPagina: false };
    }

    const capacidade = itensPorPagina();
    if (!capacidade || items.length <= capacidade) {
      return { totalPaginas: 1, paginaAtual: 0, temPagina: false };
    }

    const totalPaginas = Math.ceil(items.length / capacidade);
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

    const capacidade = itensPorPagina();
    if (capacidade && items.length > capacidade) {
      const totalPaginas = Math.ceil(items.length / capacidade);
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
    if (autoPaginaInterval) return;
    if (isModoGestor()) return;
    if (!isPlatformBrowser(platformId)) return;

    if (!itensPorPagina()) {
      setTimeout(() => iniciarAutoPagina(items), 100);
      return;
    }

    const listaInicial = items();
    const infoInicial = getInfoPagina(listaInicial);
    if (!infoInicial.temPagina || infoInicial.totalPaginas <= 1) {
      return;
    }

    autoPaginaInterval = setInterval(() => {
      const lista = items();
      const info = getInfoPagina(lista);
      if (info.temPagina && info.totalPaginas > 1) {
        avancarPagina(lista);
      } else {
        pararAutoPagina();
      }
    }, 5000);
  };

  const pararAutoPagina = () => {
    if (autoPaginaInterval) {
      clearInterval(autoPaginaInterval);
      autoPaginaInterval = null;
    }
  };

  const estaAutoPaginaRodando = () => autoPaginaInterval !== null;

  return {
    pagina,
    itensPorPagina,
    calcularItensPorPagina,
    getItensPaginados,
    getInfoPagina,
    ajustarPagina,
    iniciarAutoPagina,
    pararAutoPagina,
    estaAutoPaginaRodando
  };
}
