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
  providedIn: 'root',
})
export class AutoAtendimentoService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/autoatendimento';

  /**
   * Gera uma chave de idempotência única.
   * Deve ser chamado UMA VEZ no componente e reutilizado em caso de retry.
   */
  gerarChaveIdempotencia(): string {
    return generateIdempotencyKey();
  }

  /**
   * Cria um pedido de auto atendimento.
   * O pedido é criado diretamente (sem fila de pendentes) pois
   * o operador já está autenticado no totem.
   *
   * A chave de idempotência deve ser gerada pelo componente chamador
   * para garantir que retries/double-clicks usem a mesma chave.
   */
  criarPedido(
    request: CriarPedidoAutoAtendimentoRequest,
    idempotencyKey: string
  ): Observable<PedidoAutoAtendimentoResponse> {
    const headers = new HttpHeaders({
      'X-Idempotency-Key': idempotencyKey,
    });
    return this.http.post<PedidoAutoAtendimentoResponse>(`${this.apiUrl}/pedido`, request, {
      headers,
    });
  }

  /**
   * Busca o status de um pedido de auto atendimento.
   */
  buscarStatus(pedidoId: string): Observable<PedidoAutoAtendimentoResponse> {
    return this.http.get<PedidoAutoAtendimentoResponse>(`${this.apiUrl}/pedido/${pedidoId}/status`);
  }
}
