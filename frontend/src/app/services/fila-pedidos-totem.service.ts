import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, interval, of, startWith, switchMap, tap } from 'rxjs';
import type {
  AdicionalPedidoPendente,
  ItemPedidoPendente,
  MeioPagamentoPendente,
  PedidoPendente,
  QuantidadePendentes,
} from './fila-pedidos-mesa.service';

function generateIdempotencyKey(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Serviço da fila de pedidos do totem (auto atendimento).
 * Pedidos do totem ficam pendentes até um funcionário aceitar no painel.
 */
@Injectable({
  providedIn: 'root',
})
export class FilaPedidosTotemService {
  private http = inject(HttpClient);
  private readonly API_URL = '/api/pedidos/fila-totem';

  private _pedidosPendentes = signal<PedidoPendente[]>([]);
  private _carregando = signal(false);
  private _erro = signal<string | null>(null);

  readonly pedidosPendentes = this._pedidosPendentes.asReadonly();
  readonly carregando = this._carregando.asReadonly();
  readonly erro = this._erro.asReadonly();
  readonly quantidade = computed(() => this._pedidosPendentes().length);
  readonly existemPendentes = computed(() => this._pedidosPendentes().length > 0);

  listarPedidosPendentes(): Observable<PedidoPendente[]> {
    return this.http.get<PedidoPendente[]>(this.API_URL);
  }

  buscarQuantidade(): Observable<QuantidadePendentes> {
    return this.http.get<QuantidadePendentes>(`${this.API_URL}/quantidade`);
  }

  buscarPorId(pedidoId: string): Observable<PedidoPendente> {
    return this.http.get<PedidoPendente>(`${this.API_URL}/${pedidoId}`);
  }

  aceitarPedido(pedidoId: string, idempotencyKey: string): Observable<unknown> {
    const headers = new HttpHeaders({
      'X-Idempotency-Key': idempotencyKey,
    });
    return this.http.post(`${this.API_URL}/${pedidoId}/aceitar`, {}, { headers });
  }

  rejeitarPedido(pedidoId: string, motivo?: string): Observable<unknown> {
    return this.http.post(`${this.API_URL}/${pedidoId}/rejeitar`, { motivo });
  }

  carregarPedidos(): void {
    this._carregando.set(true);
    this._erro.set(null);
    this.listarPedidosPendentes()
      .pipe(
        tap(pedidos => {
          this._pedidosPendentes.set(pedidos);
          this._carregando.set(false);
        }),
        catchError(err => {
          this._erro.set('Erro ao carregar pedidos pendentes do totem');
          this._carregando.set(false);
          return of([]);
        })
      )
      .subscribe();
  }

  iniciarPolling(intervaloMs: number = 5000): Observable<PedidoPendente[]> {
    return interval(intervaloMs).pipe(
      startWith(0),
      switchMap(() => this.listarPedidosPendentes()),
      tap(pedidos => this._pedidosPendentes.set(pedidos)),
      catchError(err => {
        console.error('Erro no polling de pedidos pendentes totem:', err);
        return of([]);
      })
    );
  }

  formatarTempoEspera(segundos: number): string {
    if (segundos < 60) return `${segundos}s`;
    const minutos = Math.floor(segundos / 60);
    const segs = segundos % 60;
    if (minutos < 60) return `${minutos}min ${segs}s`;
    const horas = Math.floor(minutos / 60);
    const mins = minutos % 60;
    return `${horas}h ${mins}min`;
  }

  getClasseTempoEspera(segundos: number): string {
    if (segundos < 120) return 'tempo-ok';
    if (segundos < 300) return 'tempo-atencao';
    return 'tempo-urgente';
  }

  gerarChaveIdempotencia(): string {
    return generateIdempotencyKey();
  }
}
