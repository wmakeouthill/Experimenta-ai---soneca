import { signal } from '@angular/core';

const STORAGE_KEY = 'soneca:ctas-adiados';

type Adiamentos = Record<string, number>;

function lerAdiamentos(): Adiamentos {
  try {
    const salvo = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}');
    return salvo && typeof salvo === 'object' ? (salvo as Adiamentos) : {};
  } catch {
    return {};
  }
}

/**
 * Avisos (CTAs) que o cliente esconde no ✕ por um tempo.
 * Fica salvo no aparelho: sobrevive a reload e ao app instalado (PWA).
 */
export function useCtasAdiados(isBrowser: boolean) {
  const adiamentos = signal<Adiamentos>(isBrowser ? lerAdiamentos() : {});

  function estaAdiado(chave: string): boolean {
    return (adiamentos()[chave] ?? 0) > Date.now();
  }

  function adiar(chave: string, horas: number): void {
    const agora = Date.now();
    // Descarta adiamentos vencidos para o storage não crescer
    const vigentes = Object.fromEntries(
      Object.entries(adiamentos()).filter(([, ate]) => ate > agora)
    );
    const novos = { ...vigentes, [chave]: agora + horas * 3_600_000 };
    adiamentos.set(novos);
    if (!isBrowser) return;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(novos));
    } catch {
      // Storage bloqueado (aba anônima): vale só nesta sessão
    }
  }

  return { estaAdiado, adiar };
}
