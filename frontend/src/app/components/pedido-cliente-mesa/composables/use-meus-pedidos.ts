import { signal, computed, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { PedidoMesaService, HistoricoPedidoCliente } from '../../../services/pedido-mesa.service';

const ITENS_POR_PAGINA = 10;

/**
 * Composable para gerenciar o histórico de pedidos do cliente.
 */
export function useMeusPedidos(clienteIdFn: () => string | undefined) {
    const pedidoMesaService = inject(PedidoMesaService);
    const platformId = inject(PLATFORM_ID);
    const isBrowser = isPlatformBrowser(platformId);

    // Estado
    const pedidos = signal<HistoricoPedidoCliente[]>([]);
    const carregando = signal(false);
    const erro = signal<string | null>(null);
    const paginaAtual = signal(0);
    const totalPaginas = signal(0);
    const totalPedidos = signal(0);
    const carregado = signal(false);

    // Computed
    const temMaisPaginas = computed(() => paginaAtual() < totalPaginas() - 1);
    const temPaginaAnterior = computed(() => paginaAtual() > 0);
    const temMais = temMaisPaginas; // Alias para uso no template

    /**
     * Carrega a primeira página de pedidos.
     */
    function carregar(): void {
        const clienteId = clienteIdFn();
        if (!clienteId || !isBrowser) return;

        carregando.set(true);
        erro.set(null);
        paginaAtual.set(0);

        pedidoMesaService.buscarHistoricoPedidos(clienteId, 0, ITENS_POR_PAGINA).subscribe({
            next: (response) => {
                carregando.set(false);
                pedidos.set(response.pedidos);
                totalPaginas.set(response.totalPaginas);
                totalPedidos.set(response.totalPedidos);
                carregado.set(true);
            },
            error: () => {
                carregando.set(false);
                erro.set('Erro ao carregar pedidos');
            }
        });
    }

    /**
     * Carrega mais pedidos (append ao existente).
     */
    function carregarMais(): void {
        if (!temMaisPaginas()) return;

        const clienteId = clienteIdFn();
        if (!clienteId || !isBrowser) return;

        const novaPagina = paginaAtual() + 1;
        carregando.set(true);

        pedidoMesaService.buscarHistoricoPedidos(clienteId, novaPagina, ITENS_POR_PAGINA).subscribe({
            next: (response) => {
                carregando.set(false);
                // Append novos pedidos aos existentes
                pedidos.set([...pedidos(), ...response.pedidos]);
                paginaAtual.set(novaPagina);
            },
            error: () => {
                carregando.set(false);
                erro.set('Erro ao carregar pedidos');
            }
        });
    }

    /**
     * Carrega a próxima página.
     */
    function proximaPagina(): void {
        if (!temMaisPaginas()) return;

        const clienteId = clienteIdFn();
        if (!clienteId || !isBrowser) return;

        const novaPagina = paginaAtual() + 1;
        carregando.set(true);

        pedidoMesaService.buscarHistoricoPedidos(clienteId, novaPagina, ITENS_POR_PAGINA).subscribe({
            next: (response) => {
                carregando.set(false);
                pedidos.set(response.pedidos);
                paginaAtual.set(novaPagina);
            },
            error: () => {
                carregando.set(false);
                erro.set('Erro ao carregar pedidos');
            }
        });
    }

    /**
     * Carrega a página anterior.
     */
    function paginaAnterior(): void {
        if (!temPaginaAnterior()) return;

        const clienteId = clienteIdFn();
        if (!clienteId || !isBrowser) return;

        const novaPagina = paginaAtual() - 1;
        carregando.set(true);

        pedidoMesaService.buscarHistoricoPedidos(clienteId, novaPagina, ITENS_POR_PAGINA).subscribe({
            next: (response) => {
                carregando.set(false);
                pedidos.set(response.pedidos);
                paginaAtual.set(novaPagina);
            },
            error: () => {
                carregando.set(false);
                erro.set('Erro ao carregar pedidos');
            }
        });
    }

    /**
     * Recarrega a página atual.
     */
    function recarregar(): void {
        const clienteId = clienteIdFn();
        if (!clienteId || !isBrowser) return;

        carregando.set(true);

        pedidoMesaService.buscarHistoricoPedidos(clienteId, paginaAtual(), ITENS_POR_PAGINA).subscribe({
            next: (response) => {
                carregando.set(false);
                pedidos.set(response.pedidos);
                totalPaginas.set(response.totalPaginas);
                totalPedidos.set(response.totalPedidos);
            },
            error: () => {
                carregando.set(false);
                erro.set('Erro ao carregar pedidos');
            }
        });
    }

    /**
     * Limpa o estado.
     */
    function limpar(): void {
        pedidos.set([]);
        paginaAtual.set(0);
        totalPaginas.set(0);
        totalPedidos.set(0);
        carregado.set(false);
        erro.set(null);
    }

    /**
     * Formata o status do pedido para exibição.
     */
    function formatarStatus(status: string): string {
        const statusMap: Record<string, string> = {
            'PENDENTE': 'Aguardando',
            'PREPARANDO': 'Preparando',
            'PRONTO': 'Pronto',
            'FINALIZADO': 'Finalizado',
            'CANCELADO': 'Cancelado'
        };
        return statusMap[status] || status;
    }

    /**
     * Retorna a cor/classe do status.
     */
    function classeStatus(status: string): string {
        const classeMap: Record<string, string> = {
            'PENDENTE': 'status-pendente',
            'PREPARANDO': 'status-preparando',
            'PRONTO': 'status-pronto',
            'FINALIZADO': 'status-finalizado',
            'CANCELADO': 'status-cancelado'
        };
        return classeMap[status] || '';
    }

    return {
        // Estado
        pedidos: pedidos.asReadonly(),
        carregando: carregando.asReadonly(),
        erro: erro.asReadonly(),
        paginaAtual: paginaAtual.asReadonly(),
        totalPaginas: totalPaginas.asReadonly(),
        totalPedidos: totalPedidos.asReadonly(),
        carregado: carregado.asReadonly(),

        // Computed
        temMaisPaginas,
        temPaginaAnterior,
        temMais,

        // Métodos
        carregar,
        carregarMais,
        proximaPagina,
        paginaAnterior,
        recarregar,
        limpar,
        formatarStatus,
        classeStatus
    };
}
