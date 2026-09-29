import { isPlatformBrowser } from '@angular/common';
import { HttpErrorResponse, HttpEventType, HttpInterceptorFn } from '@angular/common/http';
import { inject, Injectable, NgZone, PLATFORM_ID, signal } from '@angular/core';
import { retry, tap, throwError, timer } from 'rxjs';

/**
 * Liga na 1ª falha transitória de um GET e desliga quando o servidor volta a responder (qualquer
 * método, até 4xx); o app mostra "Reconectando…" enquanto true. Tela sem nenhuma requisição depois
 * de esgotar as tentativas mantém o aviso até a próxima resposta ou até o SSE de status da loja
 * reconectar (StatusLojaService), que é o que acontece no totem parado.
 */
@Injectable({ providedIn: 'root' })
export class ConexaoStatus {
  readonly reconectando = signal(false);
}

// 0 = sem conexão (WiFi caiu, container do front reiniciando); 502/503/504 = backend fora (deploy leva ~20 s).
// 429 fica de fora: é o rate limit do nginx e insistir só piora.
const STATUS_TRANSITORIOS = [0, 502, 503, 504];
const MAX_TENTATIVAS = 4; // espera 1 + 2 + 4 + 8 = 15 s antes de desistir

const transitorio = (erro: unknown) =>
  erro instanceof HttpErrorResponse && STATUS_TRANSITORIOS.includes(erro.status);

/** Refaz GETs (idempotentes) quando o backend some por instantes. POST/PUT/DELETE nunca são refeitos. */
export const retryGetInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isPlatformBrowser(inject(PLATFORM_ID))) {
    return next(req);
  }

  const conexao = inject(ConexaoStatus);
  const zone = inject(NgZone);
  // Os pollings do /pedidos rodam fora da zona: sem zone.run o sinal muda e a tela não redesenha.
  // Cancelar a requisição não desliga o aviso: o switchMap do polling cancela a tentativa a cada 5 s.
  const marcar = (reconectando: boolean) => {
    if (conexao.reconectando() !== reconectando) {
      zone.run(() => conexao.reconectando.set(reconectando));
    }
  };

  const resposta$ = next(req).pipe(
    tap({
      next: evento => {
        if (evento.type === HttpEventType.Response) {
          marcar(false);
        }
      },
      error: erro => {
        if (erro instanceof HttpErrorResponse && !transitorio(erro)) {
          marcar(false);
        }
      }
    })
  );
  if (req.method !== 'GET') {
    return resposta$;
  }

  return resposta$.pipe(
    retry({
      count: MAX_TENTATIVAS,
      delay: (erro: unknown, tentativa: number) => {
        if (!transitorio(erro)) {
          return throwError(() => erro);
        }
        marcar(true);
        return timer(1000 * 2 ** (tentativa - 1));
      }
    })
  );
};
