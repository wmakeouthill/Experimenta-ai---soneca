import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export enum StatusPedido {
  PENDENTE = 'PENDENTE',
  PREPARANDO = 'PREPARANDO',
  PRONTO = 'PRONTO',
  FINALIZADO = 'FINALIZADO',
  CANCELADO = 'CANCELADO'
}

export interface ItemPedido {
  produtoId: string;
  produtoNome: string;
  quantidade: number;
  precoUnitario: number;
  subtotal: number;
  observacoes?: string;
}

export interface MeioPagamentoDTO {
  meioPagamento: MeioPagamento;
  valor: number;
}

export interface Pedido {
  id: string;
  numeroPedido: string;
  clienteId: string;
  clienteNome: string;
  status: StatusPedido;
  itens: ItemPedido[];
  valorTotal: number;
  observacoes?: string;
  meiosPagamento?: MeioPagamentoDTO[];
  usuarioId?: string;
  sessaoId?: string;
  dataPedido: string;
  createdAt: string;
  updatedAt: string;
}

export enum MeioPagamento {
  PIX = 'PIX',
  CARTAO_CREDITO = 'CARTAO_CREDITO',
  CARTAO_DEBITO = 'CARTAO_DEBITO',
  VALE_REFEICAO = 'VALE_REFEICAO',
  DINHEIRO = 'DINHEIRO'
}

export interface MeioPagamentoPedido {
  meioPagamento: MeioPagamento;
  valor: number;
}

export interface CriarPedidoRequest {
  clienteId: string;
  clienteNome: string;
  itens: ItemPedidoRequest[];
  observacoes?: string;
  meiosPagamento: MeioPagamentoPedido[];
  usuarioId?: string;
}

export interface ItemPedidoRequest {
  produtoId: string;
  quantidade: number;
  observacoes?: string;
}

export interface AtualizarStatusPedidoRequest {
  status: StatusPedido;
}

@Injectable({
  providedIn: 'root'
})
export class PedidoService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/pedidos';

  listar(filters?: {
    status?: StatusPedido;
    clienteId?: string;
    dataInicio?: string;
    dataFim?: string;
    sessaoId?: string;
  }): Observable<Pedido[]> {
    // Se não há filtros, faz requisição sem parâmetros
    if (!filters || Object.keys(filters).length === 0) {
      return this.http.get<Pedido[]>(this.apiUrl);
    }
    
    let params = new HttpParams();
    
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    
    if (filters.clienteId) {
      params = params.set('clienteId', filters.clienteId);
    }
    
    if (filters.dataInicio) {
      params = params.set('dataInicio', filters.dataInicio);
    }
    
    if (filters.dataFim) {
      params = params.set('dataFim', filters.dataFim);
    }
    
    if (filters.sessaoId) {
      params = params.set('sessaoId', filters.sessaoId);
    }
    
    return this.http.get<Pedido[]>(this.apiUrl, { params });
  }

  buscarPorId(id: string): Observable<Pedido> {
    return this.http.get<Pedido>(`${this.apiUrl}/${id}`);
  }

  criar(pedido: CriarPedidoRequest): Observable<Pedido> {
    return this.http.post<Pedido>(this.apiUrl, pedido);
  }

  atualizarStatus(id: string, status: StatusPedido): Observable<Pedido> {
    return this.http.put<Pedido>(`${this.apiUrl}/${id}/status`, { status });
  }

  cancelar(id: string): Observable<Pedido> {
    return this.http.put<Pedido>(`${this.apiUrl}/${id}/cancelar`, {});
  }
}

