import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type MeioPagamentoTotemGateway =
  | 'PIX'
  | 'CARTAO_CREDITO'
  | 'CARTAO_DEBITO'
  | 'CARTAO_VOUCHER';

export type StatusPagamentoTotem =
  | 'INICIADO'
  | 'AGUARDANDO_TEF'
  | 'AGUARDANDO_PIX'
  | 'APROVADO'
  | 'NEGADO'
  | 'CANCELADO'
  | 'FALHA_TECNICA'
  | 'EXPIRADO';

export interface PixCobrancaCriadaDTO {
  correlationId: string;
  txid: string;
  qrCodePayload: string;
  qrCodeBase64: string;
  copiaECola: string;
  expiracaoEm: string;
  status: StatusPagamentoTotem;
}

export interface PagamentoTotemDTO {
  id: string;
  pedidoId: string;
  correlationId: string;
  valorCentavos: number;
  meioPagamento: MeioPagamentoTotemGateway;
  status: StatusPagamentoTotem;
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
  meioPagamento: MeioPagamentoTotemGateway;
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

@Injectable({
  providedIn: 'root',
})
export class PagamentoTotemService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/v1/pagamentos-totem';

  iniciarPix(request: IniciarPagamentoPixRequest): Observable<PixCobrancaCriadaDTO> {
    return this.http.post<PixCobrancaCriadaDTO>(`${this.apiUrl}/pix/iniciar`, request);
  }

  iniciarCartao(request: IniciarPagamentoCartaoRequest): Observable<PagamentoTotemDTO> {
    return this.http.post<PagamentoTotemDTO>(`${this.apiUrl}/cartao/iniciar`, request);
  }

  confirmarCartao(request: ConfirmarPagamentoCartaoRequest): Observable<PagamentoTotemDTO> {
    return this.http.post<PagamentoTotemDTO>(`${this.apiUrl}/cartao/confirmar`, request);
  }

  buscarStatus(correlationId: string): Observable<PagamentoTotemDTO> {
    return this.http.get<PagamentoTotemDTO>(`${this.apiUrl}/${correlationId}`);
  }

  simularPixAprovado(txid: string): Observable<{ status: string }> {
    return this.http.post<{ status: string }>('/api/v1/webhooks/stone/pix', {
      txid,
      endToEndId: `E2E${Date.now()}`,
    });
  }
}
