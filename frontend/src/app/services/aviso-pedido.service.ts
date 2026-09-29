import { Injectable } from '@angular/core';
import type { PedidoPendente } from './fila-pedidos-mesa.service';

/**
 * Anuncia pedido novo da fila de mesa ou do totem por voz (Web Speech, vozes pt-BR do Windows) e
 * com notificação do Windows. Tudo nativo do navegador/Electron, sem custo.
 * O Chromium só deixa falar depois do primeiro clique na página; a permissão de notificação é
 * pedida nesse clique (no Electron já vem concedida).
 */
@Injectable({ providedIn: 'root' })
export class AvisoPedidoService {
  constructor() {
    if (typeof Notification !== 'undefined' && Notification.permission === 'default') {
      document.addEventListener('click', () => Notification.requestPermission(), { once: true });
    }
  }

  /** Anuncia os pedidos de `atuais` que não estavam em `anteriores`. */
  anunciarNovos(
    anteriores: PedidoPendente[],
    atuais: PedidoPendente[],
    frase: (pedido: PedidoPendente) => string
  ): void {
    const conhecidos = new Set(anteriores.map(p => p.id));
    atuais.filter(p => !conhecidos.has(p.id)).forEach(p => this.avisar(frase(p)));
  }

  private avisar(texto: string): void {
    if (typeof speechSynthesis !== 'undefined') {
      const fala = new SpeechSynthesisUtterance(texto);
      fala.lang = 'pt-BR';
      fala.voice = speechSynthesis.getVoices().find(v => v.lang.startsWith('pt')) ?? null;
      speechSynthesis.speak(fala);
    }
    if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
      new Notification('Novo pedido', { body: texto });
    }
  }
}
