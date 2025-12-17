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
      <img src="/assets/soneca_ai.png" alt="Soneca IA" class="chat-ia-btn-icon">
      <span class="chat-ia-tooltip">Fale comigo! 💬</span>
    </button>
  `,
    styles: [`
    .chat-ia-btn {
      position: fixed;
      bottom: 5rem;
      right: 1rem;
      width: 56px;
      height: 56px;
      border-radius: 50%;
      background: linear-gradient(135deg, #e67e22 0%, #d35400 100%);
      border: none;
      box-shadow: 0 4px 20px rgba(230, 126, 34, 0.4);
      cursor: pointer;
      z-index: 9990;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: transform 0.3s ease, box-shadow 0.3s ease;
      overflow: visible;
    }

    .chat-ia-btn:hover {
      transform: scale(1.1);
      box-shadow: 0 6px 25px rgba(230, 126, 34, 0.5);
    }

    .chat-ia-btn:active {
      transform: scale(0.95);
    }

    .chat-ia-btn.pulse {
      animation: pulse 2s infinite;
    }

    @keyframes pulse {
      0% {
        box-shadow: 0 0 0 0 rgba(230, 126, 34, 0.5);
      }
      70% {
        box-shadow: 0 0 0 15px rgba(230, 126, 34, 0);
      }
      100% {
        box-shadow: 0 0 0 0 rgba(230, 126, 34, 0);
      }
    }

    .chat-ia-btn-icon {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      object-fit: cover;
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
        width: 52px;
        height: 52px;
      }

      .chat-ia-btn-icon {
        width: 32px;
        height: 32px;
      }

      .chat-ia-tooltip {
        display: none;
      }
    }

    @media (max-width: 480px) {
      .chat-ia-btn {
        bottom: 5rem;
        right: 0.5rem;
        width: 48px;
        height: 48px;
      }

      .chat-ia-btn-icon {
        width: 30px;
        height: 30px;
      }
    }
  `]
})
export class ChatIAButtonComponent {
    readonly pulse = input<boolean>(true);
    readonly onClick = output<void>();
}
