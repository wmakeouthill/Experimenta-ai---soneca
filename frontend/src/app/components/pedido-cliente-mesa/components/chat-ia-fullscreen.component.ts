import {
    Component,
    ChangeDetectionStrategy,
    input,
    output,
    signal,
    effect,
    ViewChild,
    ElementRef,
    AfterViewChecked
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MensagemChat } from '../composables/use-chat-ia';

/**
 * Componente de Chat IA fullscreen responsivo.
 * Otimizado para uso em dispositivos móveis.
 */
@Component({
    selector: 'app-chat-ia-fullscreen',
    standalone: true,
    imports: [CommonModule, FormsModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
    <div class="chat-ia-overlay" [class.aberto]="isOpen()" (click)="fecharAoClicarFora($event)">
      <div class="chat-ia-container" (click)="$event.stopPropagation()">
        <!-- Header -->
        <header class="chat-ia-header">
          <div class="chat-ia-header-info">
            <img src="/assets/soneca_ai.webp" alt="Soneca IA" class="chat-ia-avatar">
            <div class="chat-ia-header-text">
              <h2>Soneca</h2>
              <span class="status-online">● Online</span>
            </div>
          </div>
          <div class="chat-ia-header-actions">
            <button 
              class="btn-nova-conversa" 
              (click)="onNovaConversa.emit()"
              title="Nova conversa">
              🔄
            </button>
            <button class="btn-fechar" (click)="onClose.emit()" title="Fechar">
              ✕
            </button>
          </div>
        </header>

        <!-- Mensagens -->
        <div class="chat-ia-messages" #messagesContainer>
          @for (msg of mensagens(); track msg.id) {
            <div class="chat-ia-message" [class.user]="msg.from === 'user'" [class.assistant]="msg.from === 'assistant'">
              @if (msg.from === 'assistant') {
                <img src="/assets/soneca_ai.webp" alt="Soneca" class="message-avatar">
              }
              <div class="message-bubble">
                <p class="message-text">{{ msg.text }}</p>
                <span class="message-time">{{ formatTime(msg.timestamp) }}</span>
              </div>
            </div>
          }
          
          @if (isLoading()) {
            <div class="chat-ia-message assistant">
              <img src="/assets/soneca_ai.webp" alt="Soneca" class="message-avatar">
              <div class="message-bubble typing">
                <div class="typing-indicator">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
              </div>
            </div>
          }
        </div>

        <!-- Input -->
        <footer class="chat-ia-input-area">
          <div class="input-wrapper">
            <input
              #chatInput
              type="text"
              [ngModel]="inputText()"
              (ngModelChange)="onInputChange.emit($event)"
              (keydown.enter)="enviar()"
              placeholder="Digite sua mensagem..."
              [disabled]="isLoading()"
              class="chat-ia-input"
              autocomplete="off">
            <button 
              class="btn-enviar" 
              [disabled]="!canSend()"
              (click)="enviar()">
              @if (isLoading()) {
                <span class="spinner-mini"></span>
              } @else {
                ➤
              }
            </button>
          </div>
        </footer>
      </div>
    </div>
  `,
    styles: [`
    .chat-ia-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.5);
      z-index: 10000;
      display: flex;
      align-items: center;
      justify-content: center;
      opacity: 0;
      visibility: hidden;
      transition: opacity 0.3s ease, visibility 0.3s ease;
    }

    .chat-ia-overlay.aberto {
      opacity: 1;
      visibility: visible;
    }

    .chat-ia-container {
      width: 100%;
      height: 100%;
      max-width: 100dvw;
      max-height: 100dvh;
      background: var(--bg-primary, #1a1a2e);
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    /* Desktop: chat como modal */
    @media (min-width: 769px) {
      .chat-ia-container {
        width: 450px;
        max-width: 90vw;
        height: 600px;
        max-height: 85vh;
        border-radius: 16px;
        box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
      }
    }

    /* Header */
    .chat-ia-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1rem 1.25rem;
      background: linear-gradient(135deg, #e67e22 0%, #d35400 100%);
      color: white;
      flex-shrink: 0;
    }

    .chat-ia-header-info {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .chat-ia-avatar {
      width: 42px;
      height: 42px;
      border-radius: 50%;
      border: 2px solid rgba(255, 255, 255, 0.3);
      object-fit: cover;
    }

    .chat-ia-header-text h2 {
      margin: 0;
      font-size: 1.125rem;
      font-weight: 600;
    }

    .status-online {
      font-size: 0.75rem;
      opacity: 0.9;
      color: #2ecc71;
    }

    .chat-ia-header-actions {
      display: flex;
      gap: 0.5rem;
    }

    .btn-nova-conversa,
    .btn-fechar {
      background: rgba(255, 255, 255, 0.2);
      border: none;
      color: white;
      width: 36px;
      height: 36px;
      border-radius: 50%;
      cursor: pointer;
      font-size: 1rem;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.2s ease;
    }

    .btn-nova-conversa:hover,
    .btn-fechar:hover {
      background: rgba(255, 255, 255, 0.3);
    }

    /* Messages */
    .chat-ia-messages {
      flex: 1;
      overflow-y: auto;
      padding: 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      background: var(--bg-secondary, #16213e);
      -webkit-overflow-scrolling: touch;
      overscroll-behavior: contain;
    }

    .chat-ia-message {
      display: flex;
      gap: 0.5rem;
      max-width: 85%;
      animation: fadeIn 0.3s ease;
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(10px); }
      to { opacity: 1; transform: translateY(0); }
    }

    .chat-ia-message.user {
      align-self: flex-end;
      flex-direction: row-reverse;
    }

    .chat-ia-message.assistant {
      align-self: flex-start;
    }

    .message-avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      object-fit: cover;
      flex-shrink: 0;
    }

    .message-bubble {
      padding: 0.75rem 1rem;
      border-radius: 16px;
      max-width: 100%;
      word-wrap: break-word;
    }

    .chat-ia-message.user .message-bubble {
      background: linear-gradient(135deg, #e67e22 0%, #d35400 100%);
      color: white;
      border-bottom-right-radius: 4px;
    }

    .chat-ia-message.assistant .message-bubble {
      background: var(--bg-tertiary, #1f2b47);
      color: var(--text-primary, #e8e8e8);
      border-bottom-left-radius: 4px;
    }

    .message-text {
      margin: 0;
      font-size: 0.95rem;
      line-height: 1.4;
      white-space: pre-wrap;
    }

    .message-time {
      display: block;
      font-size: 0.7rem;
      opacity: 0.6;
      margin-top: 0.35rem;
      text-align: right;
    }

    /* Typing indicator */
    .message-bubble.typing {
      padding: 0.875rem 1.25rem;
    }

    .typing-indicator {
      display: flex;
      gap: 4px;
    }

    .typing-indicator span {
      width: 8px;
      height: 8px;
      background: var(--text-secondary, #aaa);
      border-radius: 50%;
      animation: typing 1.4s infinite ease-in-out;
    }

    .typing-indicator span:nth-child(1) { animation-delay: 0s; }
    .typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
    .typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

    @keyframes typing {
      0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
      30% { transform: translateY(-6px); opacity: 1; }
    }

    /* Input area */
    .chat-ia-input-area {
      padding: 0.875rem 1rem;
      background: var(--bg-primary, #1a1a2e);
      border-top: 1px solid rgba(255, 255, 255, 0.1);
      flex-shrink: 0;
    }

    /* Safe area para iPhone X+ */
    @supports (padding: max(0px)) {
      .chat-ia-input-area {
        padding-bottom: max(0.875rem, env(safe-area-inset-bottom));
      }
    }

    .input-wrapper {
      display: flex;
      gap: 0.5rem;
      align-items: center;
    }

    .chat-ia-input {
      flex: 1;
      background: var(--bg-secondary, #16213e);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 24px;
      padding: 0.75rem 1rem;
      color: var(--text-primary, #e8e8e8);
      font-size: 1rem;
      outline: none;
      transition: border-color 0.2s ease;
    }

    .chat-ia-input:focus {
      border-color: #e67e22;
    }

    .chat-ia-input::placeholder {
      color: var(--text-secondary, #888);
    }

    .chat-ia-input:disabled {
      opacity: 0.6;
    }

    .btn-enviar {
      width: 44px;
      height: 44px;
      border-radius: 50%;
      background: linear-gradient(135deg, #e67e22 0%, #d35400 100%);
      border: none;
      color: white;
      font-size: 1.25rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: transform 0.2s ease, opacity 0.2s ease;
      flex-shrink: 0;
    }

    .btn-enviar:hover:not(:disabled) {
      transform: scale(1.05);
    }

    .btn-enviar:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .spinner-mini {
      width: 18px;
      height: 18px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: white;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    /* Mobile optimizations */
    @media (max-width: 768px) {
      .chat-ia-header {
        padding-top: max(1rem, env(safe-area-inset-top));
      }

      .chat-ia-messages {
        padding: 0.75rem;
      }

      .chat-ia-message {
        max-width: 90%;
      }

      .message-text {
        font-size: 0.9rem;
      }
    }
  `]
})
export class ChatIAFullscreenComponent implements AfterViewChecked {
    // Inputs
    readonly isOpen = input.required<boolean>();
    readonly isLoading = input<boolean>(false);
    readonly inputText = input<string>('');
    readonly canSend = input<boolean>(false);
    readonly mensagens = input<MensagemChat[]>([]);

    // Outputs
    readonly onClose = output<void>();
    readonly onSend = output<void>();
    readonly onInputChange = output<string>();
    readonly onNovaConversa = output<void>();

    @ViewChild('messagesContainer') private messagesContainer?: ElementRef<HTMLDivElement>;
    @ViewChild('chatInput') private chatInput?: ElementRef<HTMLInputElement>;

    private shouldScrollToBottom = true;

    constructor() {
        // Auto-scroll quando novas mensagens chegam
        effect(() => {
            this.mensagens(); // Track changes
            this.shouldScrollToBottom = true;
        });

        // Foca no input quando abre
        effect(() => {
            if (this.isOpen()) {
                setTimeout(() => this.focusInput(), 100);
            }
        });
    }

    ngAfterViewChecked(): void {
        if (this.shouldScrollToBottom) {
            this.scrollToBottom();
            this.shouldScrollToBottom = false;
        }
    }

    formatTime(date: Date): string {
        return date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    }

    enviar(): void {
        if (this.canSend()) {
            this.onSend.emit();
        }
    }

    fecharAoClicarFora(event: Event): void {
        if ((event.target as HTMLElement).classList.contains('chat-ia-overlay')) {
            this.onClose.emit();
        }
    }

    private scrollToBottom(): void {
        if (this.messagesContainer?.nativeElement) {
            const el = this.messagesContainer.nativeElement;
            el.scrollTop = el.scrollHeight;
        }
    }

    private focusInput(): void {
        if (this.chatInput?.nativeElement) {
            this.chatInput.nativeElement.focus();
        }
    }
}
