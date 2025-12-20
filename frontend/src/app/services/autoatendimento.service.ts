import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ItemPedidoAutoAtendimentoRequest {
    produtoId: string;
    quantidade: number;
    observacao?: string;
    adicionais?: AdicionalPedidoAutoAtendimentoRequest[];
}

export interface AdicionalPedidoAutoAtendimentoRequest {
    adicionalId: string;
    quantidade: number;
}

export interface MeioPagamentoAutoAtendimentoRequest {
    tipo: 'PIX' | 'CARTAO_CREDITO' | 'CARTAO_DEBITO' | 'DINHEIRO';
    valor: number;
}

export interface CriarPedidoAutoAtendimentoRequest {
    nomeCliente?: string;
    observacao?: string;
    itens: ItemPedidoAutoAtendimentoRequest[];
    meiosPagamento: MeioPagamentoAutoAtendimentoRequest[];
}

export interface PedidoAutoAtendimentoResponse {
    id: string;
    numeroPedido: number;
    nomeCliente?: string;
    status: string;
    valorTotal: number;
    dataPedido: string;
}

/**
 * Service para operações do auto atendimento (totem).
 * Endpoints autenticados com JWT do operador logado.
 */
@Injectable({
    providedIn: 'root'
})
export class AutoAtendimentoService {
    private readonly http = inject(HttpClient);
    private readonly apiUrl = '/api/autoatendimento';

    /**
     * Cria um pedido de auto atendimento.
     * O pedido é criado diretamente (sem fila de pendentes) pois
     * o operador já está autenticado no totem.
     */
    criarPedido(request: CriarPedidoAutoAtendimentoRequest): Observable<PedidoAutoAtendimentoResponse> {
        return this.http.post<PedidoAutoAtendimentoResponse>(`${this.apiUrl}/pedido`, request);
    }

    /**
     * Busca o status de um pedido de auto atendimento.
     */
    buscarStatus(pedidoId: string): Observable<PedidoAutoAtendimentoResponse> {
        return this.http.get<PedidoAutoAtendimentoResponse>(`${this.apiUrl}/pedido/${pedidoId}/status`);
    }
}
