import { HttpClient } from '@angular/common/http';
import { inject, Injectable, NgZone } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

/**
 * Status possíveis da loja para pedidos.
 */
export enum StatusLoja {
  /** Loja funcionando normalmente */
  ABERTA = 'ABERTA',
  /** Loja temporariamente indisponível (alta demanda, etc.) */
  PAUSADA = 'PAUSADA',
  /** Não há sessão de trabalho ativa */
  FECHADA = 'FECHADA',
}

/**
 * Resposta do endpoint de status da loja.
 */
export interface StatusLojaResponse {
  status: StatusLoja;
  mensagem: string | null;
  numeroSessao: number | null;
}

/**
 * Serviço para verificar o status da loja (sessão de trabalho).
 * Usado para bloquear pedidos quando a loja está fechada ou pausada.
 */
@Injectable({
  providedIn: 'root',
})
export class StatusLojaService {
  private readonly http = inject(HttpClient);
  private readonly zone = inject(NgZone);
  private readonly apiUrl = '/api/public/status-loja';

  /**
   * Verifica o status atual da loja (request único).
   *
   * @returns Observable com o status da loja
   */
  verificarStatus(): Observable<StatusLojaResponse> {
    return this.http.get<StatusLojaResponse>(this.apiUrl).pipe(
      catchError(() => {
        // Em caso de erro, assume fechada por segurança
        return of({
          status: StatusLoja.FECHADA,
          mensagem: 'Não foi possível verificar o status da loja.',
          numeroSessao: null,
        });
      })
    );
  }

  /**
   * Conecta ao stream de eventos da loja (SSE).
   * Recebe atualizações em tempo real quando o status muda.
   */
  conectarStream(): Observable<StatusLojaResponse> {
    return new Observable<StatusLojaResponse>(observer => {
      const eventSource = new EventSource(`${this.apiUrl}/stream`);

      // Listener para status (mudança real)
      eventSource.addEventListener('status', (event: MessageEvent) => {
        this.zone.run(() => {
          try {
            const data = JSON.parse(event.data) as StatusLojaResponse;
            observer.next(data);
          } catch (e) {
            console.error('Erro ao processar evento de status:', e);
          }
        });
      });

      // Listener para ping (heartbeat) - apenas para manter conexão
      eventSource.addEventListener('ping', () => {
        // Heartbeat - conexão ativa
      });

      eventSource.onerror = error => {
        this.zone.run(() => {
          if (eventSource.readyState === EventSource.CLOSED) {
            console.error('Conexão SSE fechada permanentemente.');
          } else if (eventSource.readyState === EventSource.CONNECTING) {
            console.warn('Conexão SSE perdida. Tentando reconectar...');
          }
        });
      };

      return () => {
        eventSource.close();
      };
    });
  }

  /**
   * Verifica se a loja está aberta para receber pedidos.
   */
  estaAberta(): Observable<boolean> {
    return this.verificarStatus().pipe(map(response => response.status === StatusLoja.ABERTA));
  }
}
