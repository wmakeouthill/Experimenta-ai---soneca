import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type MeioPagamentoGateway =
  | 'PIX'
  | 'CARTAO_CREDITO'
  | 'CARTAO_DEBITO'
  | 'CARTAO_VOUCHER';

export type StatusPagamento =
  | 'INICIADO'
  | 'AGUARDANDO_TEF'
  | 'AGUARDANDO_PIX'
  | 'APROVADO'
  | 'NEGADO'
  | 'CANCELADO'
  | 'FALHA_TECNICA'
  | 'EXPIRADO';

export type CanalPagamento = 'TOTEM' | 'MESA';

export interface PixCobrancaCriadaDTO {
  correlationId: string;
  txid: string;
  qrCodePayload: string;
  qrCodeBase64: string;
  copiaECola: string;
  expiracaoEm: string;
  status: StatusPagamento;
}

export interface PagamentoDTO {
  id: string;
  canal: CanalPagamento;
  gateway: 'SIMULADO' | 'STONE' | 'GETNET';
  pedidoId?: string;
  pedidoPendenteId?: string;
  contaMesaId?: string;
  correlationId: string;
  valorCentavos: number;
  meioPagamento: MeioPagamentoGateway;
  status: StatusPagamento;
  nsuTef?: string;
  bandeira?: string;
  codigoAutorizacao?: string;
  codigoAdquirente?: string;
  comprovanteCliente?: string;
  pixTxid?: string;
  pixQrCodePayload?: string;
  pixQrCodeBase64?: string;
  pixCopiaECola?: string;
  pixEndToEndId?: string;
  pixExpiracaoEm?: string;
  motivo?: string;
  iniciadoEm: string;
  finalizadoEm?: string;
}

export interface IniciarPagamentoPixRequest {
  pedidoId: string;
  correlationId: string;
}

export interface IniciarPagamentoCartaoRequest {
  pedidoId: string;
  meioPagamento: MeioPagamentoGateway;
  correlationId: string;
}

export interface ConfirmarPagamentoCartaoRequest {
  correlationId: string;
  aprovado: boolean;
  nsuTef?: string;
  bandeira?: string;
  codigoAutorizacao?: string;
  codigoAdquirente?: string;
  comprovanteCliente?: string;
  motivo?: string;
}

export interface PedidoMesaComPix {
  pedido: { id: string; valorTotal: number; aguardandoPagamento: boolean };
  pagamento: PixCobrancaCriadaDTO;
}

@Injectable({
  providedIn: 'root',
})
export class PagamentoService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/v1/pagamentos';

  iniciarPix(request: IniciarPagamentoPixRequest): Observable<PixCobrancaCriadaDTO> {
    return this.http.post<PixCobrancaCriadaDTO>(`${this.apiUrl}/pix/iniciar`, request);
  }

  iniciarCartao(request: IniciarPagamentoCartaoRequest): Observable<PagamentoDTO> {
    return this.http.post<PagamentoDTO>(`${this.apiUrl}/cartao-presencial/iniciar`, request);
  }

  confirmarCartao(request: ConfirmarPagamentoCartaoRequest): Observable<PagamentoDTO> {
    return this.http.post<PagamentoDTO>(`${this.apiUrl}/cartao-presencial/confirmar`, request);
  }

  buscarStatus(correlationId: string): Observable<PagamentoDTO> {
    return this.http.get<PagamentoDTO>(`${this.apiUrl}/${correlationId}`);
  }

  /** Cria um pedido de mesa pré-pago via PIX (o pedido só é liberado à cozinha após a aprovação). */
  criarPedidoMesaComPix(request: unknown, correlationId: string): Observable<PedidoMesaComPix> {
    return this.http.post<PedidoMesaComPix>(
      '/api/public/mesa/pedido/pix',
      request,
      { headers: { 'X-Correlation-Id': correlationId } },
    );
  }

  cancelar(correlationId: string, motivo?: string): Observable<PagamentoDTO> {
    return this.http.post<PagamentoDTO>(`${this.apiUrl}/cancelar`, { correlationId, motivo });
  }

  /** Somente gateway SIMULADO: dispara o webhook simulado aprovando o PIX. */
  simularPixAprovado(txid: string): Observable<{ status: string }> {
    return this.http.post<{ status: string }>('/api/v1/webhooks/stone/pix', {
      txid,
      endToEndId: `E2E${Date.now()}`,
    });
  }
}
