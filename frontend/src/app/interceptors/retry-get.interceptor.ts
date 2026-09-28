import { isPlatformBrowser } from '@angular/common';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { computed, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { finalize, retry, throwError, timer } from 'rxjs';

/** Quantas requisições estão refazendo tentativa agora; o app mostra "Reconectando…" enquanto > 0. */
@Injectable({ providedIn: 'root' })
export class ConexaoStatus {
  readonly tentativasEmCurso = signal(0);
  readonly reconectando = computed(() => this.tentativasEmCurso() > 0);
}

// 0 = sem conexão (WiFi caiu, container do front reiniciando); 502/503/504 = backend fora (deploy leva ~20 s).
// 429 fica de fora: é o rate limit do nginx e insistir só piora.
const STATUS_TRANSITORIOS = [0, 502, 503, 504];
const MAX_TENTATIVAS = 4; // espera 1 + 2 + 4 + 8 = 15 s antes de desistir

/** Refaz GETs (idempotentes) quando o backend some por instantes. POST/PUT/DELETE nunca são refeitos. */
export const retryGetInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.method !== 'GET' || !isPlatformBrowser(inject(PLATFORM_ID))) {
    return next(req);
  }

  const conexao = inject(ConexaoStatus);
  let contando = false;

  return next(req).pipe(
    retry({
      count: MAX_TENTATIVAS,
      delay: (erro: unknown, tentativa: number) => {
        if (!(erro instanceof HttpErrorResponse) || !STATUS_TRANSITORIOS.includes(erro.status)) {
          return throwError(() => erro);
        }
        if (!contando) {
          contando = true;
          conexao.tentativasEmCurso.update(n => n + 1);
        }
        return timer(1000 * 2 ** (tentativa - 1));
      }
    }),
    finalize(() => {
      if (contando) {
        conexao.tentativasEmCurso.update(n => n - 1);
      }
    })
  );
};
