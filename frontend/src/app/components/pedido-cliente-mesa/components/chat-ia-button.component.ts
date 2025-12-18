import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Botão flutuante para abrir o Chat IA.
 * Exibe o ícone do Soneca com indicador de disponibilidade.
 */
@Component({
    selector: 'app-chat-ia-button',
    standalone: true,
    imports: [CommonModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
    <button 
      class="chat-ia-btn" 
      [class.pulse]="pulse()"
      (click)="onClick.emit()"
      [attr.aria-label]="'Abrir chat com Soneca IA'">
      <img 
        src="/assets/soneca_ai.webp" 
        alt="Soneca IA" 
        class="chat-ia-btn-icon"
        loading="eager"
        decoding="async"
        fetchpriority="high">
      <span class="chat-ia-tooltip">Fale comigo! 💬</span>
    </button>
  `,
    styles: [`
    .chat-ia-btn {
      position: fixed;
      bottom: 5rem;
      right: 1rem;
      width: 72px;
      height: 72px;
      border-radius: 50%;
      background: transparent;
      border: none;
      box-shadow: none;
      cursor: pointer;
      z-index: 9990;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: transform 0.3s ease, filter 0.3s ease;
      overflow: visible;
      padding: 0;
    }

    .chat-ia-btn:hover {
      transform: scale(1.1);
      filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.3));
    }

    .chat-ia-btn:active {
      transform: scale(0.95);
    }

    .chat-ia-btn.pulse {
      animation: pulse 2s infinite;
    }

    @keyframes pulse {
      0% {
        filter: drop-shadow(0 0 0 rgba(230, 126, 34, 0.5));
      }
      70% {
        filter: drop-shadow(0 0 15px rgba(230, 126, 34, 0.6));
      }
      100% {
        filter: drop-shadow(0 0 0 rgba(230, 126, 34, 0));
      }
    }

    .chat-ia-btn-icon {
      width: 72px;
      height: 72px;
      border-radius: 50%;
      object-fit: contain;
    }

    .chat-ia-tooltip {
      position: absolute;
      right: calc(100% + 0.75rem);
      top: 50%;
      transform: translateY(-50%);
      background: var(--bg-secondary, #16213e);
      color: var(--text-primary, #e8e8e8);
      padding: 0.5rem 0.75rem;
      border-radius: 8px;
      font-size: 0.85rem;
      white-space: nowrap;
      opacity: 0;
      visibility: hidden;
      transition: opacity 0.2s ease, visibility 0.2s ease;
      box-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
      pointer-events: none;
    }

    .chat-ia-tooltip::after {
      content: '';
      position: absolute;
      right: -6px;
      top: 50%;
      transform: translateY(-50%);
      border: 6px solid transparent;
      border-left-color: var(--bg-secondary, #16213e);
    }

    .chat-ia-btn:hover .chat-ia-tooltip {
      opacity: 1;
      visibility: visible;
    }

    /* Responsivo */
    @media (max-width: 768px) {
      .chat-ia-btn {
        bottom: 5.5rem;
        right: 0.75rem;
        width: 64px;
        height: 64px;
      }

      .chat-ia-btn-icon {
        width: 64px;
        height: 64px;
      }

      .chat-ia-tooltip {
        display: none;
      }
    }

    @media (max-width: 480px) {
      .chat-ia-btn {
        bottom: 5rem;
        right: 0.5rem;
        width: 60px;
        height: 60px;
      }

      .chat-ia-btn-icon {
        width: 60px;
        height: 60px;
      }
    }
  `]
})
export class ChatIAButtonComponent {
    readonly pulse = input<boolean>(true);
    readonly onClick = output<void>();
}
