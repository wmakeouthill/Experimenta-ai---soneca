import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Gera uma chave de idempotência única para requisições.
 */
function generateIdempotencyKey(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export interface ItemPedidoAutoAtendimentoRequest {
  produtoId: string;
  quantidade: number;
  observacoes?: string;
  adicionais?: AdicionalPedidoAutoAtendimentoRequest[];
}

export interface AdicionalPedidoAutoAtendimentoRequest {
  adicionalId: string;
  quantidade: number;
}

export interface MeioPagamentoAutoAtendimentoRequest {
  meioPagamento: 'PIX' | 'CARTAO_CREDITO' | 'CARTAO_DEBITO' | 'VALE_REFEICAO' | 'DINHEIRO';
  valor: number;
  valorPagoDinheiro?: number;
}

export interface CriarPedidoAutoAtendimentoRequest {
  nomeCliente?: string;
  observacao?: string;
  itens: ItemPedidoAutoAtendimentoRequest[];
  meiosPagamento: MeioPagamentoAutoAtendimentoRequest[];
}

/** Resposta quando o pedido do totem foi enviado para a fila de aceitação. */
export interface PedidoTotemNaFilaResponse {
  id: string;
  status: string;
  mensagem: string;
}

/** Resposta ao buscar status de um pedido já aceito (pedido real). */
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
 * O pedido é enviado para a fila de aceitação (igual ao fluxo de mesa)
 * e só vira pedido real quando um funcionário aceitar no painel.
 */
@Injectable({
  providedIn: 'root',
})
export class AutoAtendimentoService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/autoatendimento';

  gerarChaveIdempotencia(): string {
    return generateIdempotencyKey();
  }

  /**
   * Envia o pedido do totem para a fila de aceitação.
   * Retorna id e mensagem; o pedido só vira "aguardando" após aceite no painel.
   */
  criarPedido(
    request: CriarPedidoAutoAtendimentoRequest,
    idempotencyKey: string
  ): Observable<PedidoTotemNaFilaResponse> {
    const headers = new HttpHeaders({
      'X-Idempotency-Key': idempotencyKey,
    });
    return this.http.post<PedidoTotemNaFilaResponse>(`${this.apiUrl}/pedido`, request, {
      headers,
    });
  }

  /** Busca o status de um pedido já aceito (por id do pedido real). */
  buscarStatus(pedidoId: string): Observable<PedidoAutoAtendimentoResponse> {
    return this.http.get<PedidoAutoAtendimentoResponse>(`${this.apiUrl}/pedido/${pedidoId}/status`);
  }
}
