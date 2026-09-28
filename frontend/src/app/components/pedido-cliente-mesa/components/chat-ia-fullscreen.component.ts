import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  effect,
  computed,
  ViewChild,
  ElementRef,
  AfterViewChecked,
  Pipe,
  PipeTransform
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MensagemChat, ProdutoDestacado, ConversaSalva } from '../composables/use-chat-ia';
import { markdownChatParaHtml } from '../../../shared/utils/markdown-chat';

/** Pipe pura: converte cada resposta uma vez só, não a cada ciclo de detecção. */
@Pipe({ name: 'markdownChat', standalone: true })
export class MarkdownChatPipe implements PipeTransform {
  transform(texto: string): string {
    return markdownChatParaHtml(texto);
  }
}

/**
 * Componente de Chat IA fullscreen responsivo.
 * Otimizado para uso em dispositivos móveis.
 * Suporta renderização de cards de produtos com opção de adicionar ao carrinho.
 */
@Component({
  selector: 'app-chat-ia-fullscreen',
  standalone: true,
  imports: [CommonModule, FormsModule, MarkdownChatPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="chat-ia-overlay" [class.aberto]="isOpen() && !isHidden()" (click)="fecharAoClicarFora($event)">
      <div class="chat-ia-container" (click)="$event.stopPropagation()">
        <!-- Header -->
        <header class="chat-ia-header">
          <div class="chat-ia-header-info">
            <img src="/assets/chat-avatar.webp" alt="Soneca IA" class="chat-ia-avatar">
            <div class="chat-ia-header-text">
              <h2>Soneca</h2>
              <span class="status-online">● Online</span>
            </div>
          </div>

          <div class="chat-ia-header-actions">
            @if (quantidadeItensCarrinho() > 0) {
              <button
                class="btn-header btn-carrinho-header"
                [class.bounce]="animarCarrinho()"
                (click)="onAbrirCarrinho.emit()"
                aria-label="Ver carrinho">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z"/><path d="M3 6h18"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
                <span class="carrinho-badge">{{ quantidadeItensCarrinho() }}</span>
              </button>
            }
            <button class="btn-header" (click)="onNovaConversa.emit()" aria-label="Nova conversa">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/><path d="M12 7v6M9 10h6"/></svg>
            </button>
            <button
              class="btn-header"
              [class.ativo]="mostrarHistorico()"
              (click)="onToggleHistorico.emit()"
              aria-label="Conversas anteriores">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/><path d="M12 7v5l4 2"/></svg>
            </button>
            <button class="btn-header" (click)="onClose.emit()" aria-label="Fechar chat">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"/></svg>
            </button>
          </div>
        </header>

        <!-- Mensagens -->
        <div class="chat-ia-messages" #messagesContainer>
          @for (msg of mensagens(); track msg.id) {
            <div class="chat-ia-message" [class.user]="msg.from === 'user'" [class.assistant]="msg.from === 'assistant'">
              @if (msg.from === 'assistant') {
                <img src="/assets/chat-avatar.webp" alt="Soneca" class="message-avatar">
              }
              <div class="message-content">
                <div class="message-bubble">
                  @if (msg.from === 'assistant') {
                    <div class="message-text markdown" [innerHTML]="msg.text | markdownChat"></div>
                  } @else {
                    <p class="message-text">{{ msg.text }}</p>
                  }
                  <span class="message-time">{{ formatTime(msg.timestamp) }}</span>
                </div>

                <!-- Cards de produtos destacados, agrupados por categoria -->
                @if (msg.produtosDestacados && msg.produtosDestacados.length > 0) {
                  <div class="produtos-destacados">
                    @for (categoria of agruparPorCategoria(msg.produtosDestacados); track categoria.nome) {
                      <div class="categoria-grupo">
                        <h5 class="categoria-titulo">{{ categoria.nome }}</h5>
                        <div class="categoria-cards">
                          @for (produto of categoria.produtos; track produto.id) {
                            <div class="produto-card"
                                 [class.indisponivel]="!produto.disponivel"
                                 (click)="adicionarAoCarrinho(produto)">
                              @if (produto.imagemUrl) {
                                <img [src]="produto.imagemUrl" [alt]="produto.nome" class="produto-imagem" loading="lazy">
                              } @else {
                                <div class="produto-imagem-placeholder">🍔</div>
                              }
                              <h4 class="produto-nome">{{ produto.nome }}</h4>
                              @if (produto.descricao) {
                                <p class="produto-descricao">{{ produto.descricao }}</p>
                              }
                              <div class="produto-footer">
                                <span class="produto-preco">{{ formatarPreco(produto.preco) }}</span>
                                @if (produto.disponivel) {
                                  <button
                                    class="btn-adicionar"
                                    (click)="adicionarAoCarrinho(produto); $event.stopPropagation()"
                                    [attr.aria-label]="'Adicionar ' + produto.nome + ' ao carrinho'">
                                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
                                  </button>
                                } @else {
                                  <span class="produto-indisponivel">Indisponível</span>
                                }
                              </div>
                            </div>
                          }
                        </div>
                      </div>
                    }
                  </div>
                }
              </div>
            </div>
          }

          @if (isLoading()) {
            <div class="chat-ia-message assistant">
              <img src="/assets/chat-avatar.webp" alt="Soneca" class="message-avatar">
              <div class="message-bubble typing">
                <div class="typing-indicator">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
              </div>
            </div>
          }

          <!-- Sugestões até o cliente mandar a primeira mensagem -->
          @if (mostrarSugestoes()) {
            <div class="sugestoes">
              @for (sugestao of sugestoes; track sugestao) {
                <button class="sugestao-chip" (click)="enviarSugestao(sugestao)">{{ sugestao }}</button>
              }
            </div>
          }
        </div>

        <!-- Histórico: gaveta que sobe por cima do input -->
        @if (mostrarHistorico()) {
          <div class="historico-backdrop" (click)="onToggleHistorico.emit()"></div>
          <div class="historico-panel" role="dialog" aria-label="Conversas anteriores">
            <div class="historico-header">
              <h3>Conversas anteriores</h3>
              <button class="btn-header" (click)="onToggleHistorico.emit()" aria-label="Fechar conversas anteriores">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"/></svg>
              </button>
            </div>
            <div class="historico-lista">
              @if (historicoConversas().length === 0) {
                <div class="historico-vazio">
                  <span>💬</span>
                  <p>Nenhuma conversa anterior</p>
                </div>
              } @else {
                @for (conversa of historicoConversas(); track conversa.id) {
                  <div class="historico-item" (click)="onCarregarConversa.emit(conversa.id)">
                    <div class="historico-item-titulo">{{ conversa.titulo }}</div>
                    <div class="historico-item-preview">{{ conversa.previewUltimaMensagem }}</div>
                    <div class="historico-item-data">{{ formatDate(conversa.dataUltimaMensagem) }}</div>
                    <button
                      class="btn-remover-conversa"
                      (click)="onRemoverConversa.emit(conversa.id); $event.stopPropagation()"
                      aria-label="Remover conversa">
                      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6"/></svg>
                    </button>
                  </div>
                }
              }
            </div>
          </div>
        }

        <!-- Input -->
        <footer class="chat-ia-input-area" #inputArea>
          <div class="input-wrapper">
            <input
              #chatInput
              type="text"
              name="mensagem"
              [ngModel]="inputText()"
              (ngModelChange)="onInputChange.emit($event)"
              (keydown.enter)="enviar()"
              (focus)="onInputFocus()"
              placeholder="Pergunte ao Soneca…"
              aria-label="Mensagem para o Soneca"
              [disabled]="isLoading()"
              class="chat-ia-input"
              autocomplete="off"
              enterkeyhint="send">
            <button
              class="btn-enviar"
              [disabled]="!canSend()"
              (click)="enviar()"
              aria-label="Enviar mensagem">
              @if (isLoading()) {
                <span class="spinner-mini"></span>
              } @else {
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 19V5M5 12l7-7 7 7"/></svg>
              }
            </button>
          </div>
        </footer>
      </div>
    </div>
  `,
  styles: [`
    /* Tokens próprios do chat: o :host da pedido-mesa define --text-primary
       escuro (tema claro) e ele vazava para cá, deixando texto marrom no azul. */
    :host {
      --chat-bg: #16213e;
      --chat-surface: #1a1a2e;
      --chat-bubble: #24304f;
      --chat-text: #eef0f6;
      --chat-text-2: #b8bccb;
      --chat-text-3: #8f95ab;
      --chat-border: rgba(255, 255, 255, 0.08);
      --chat-accent: #ff6b35;
      --chat-accent-soft: #ffb08a;
      /* Fundo laranja com texto branco: o #ff6b35 dá ~2.9:1, este passa no AA */
      --chat-accent-strong: #c2410c;
      --chat-ok: #5fd38d;
      --chat-danger: #ff8a80;
    }

    .chat-ia-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.5);
      z-index: 10000;
      display: flex;
      align-items: center;
      justify-content: center;
      opacity: 0;
      visibility: hidden;
      transition: opacity 0.3s ease, visibility 0.3s ease;
      font-family: var(--fonte-app);
      color: var(--chat-text);
      -webkit-font-smoothing: antialiased;
      -moz-osx-font-smoothing: grayscale;
    }

    .chat-ia-overlay.aberto {
      opacity: 1;
      visibility: visible;
    }

    .chat-ia-container {
      position: relative;
      width: 100%;
      height: 100vh;
      /* Altura visual: acompanha o teclado virtual no mobile */
      height: 100dvh;
      background: var(--chat-surface);
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

    svg {
      width: 22px;
      height: 22px;
      fill: none;
      stroke: currentColor;
      stroke-width: 2;
      stroke-linecap: round;
      stroke-linejoin: round;
    }

    button {
      font-family: inherit;
      -webkit-tap-highlight-color: transparent;
    }

    button:focus-visible {
      outline: 2px solid var(--chat-accent-soft);
      outline-offset: 2px;
    }

    /* Header */
    .chat-ia-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 0.25rem 0.5rem 1rem;
      padding-top: max(0.5rem, env(safe-area-inset-top));
      background: var(--chat-surface);
      border-bottom: 1px solid var(--chat-border);
      flex-shrink: 0;
    }

    .chat-ia-header-info {
      display: flex;
      align-items: center;
      gap: 0.625rem;
      flex: 1;
      min-width: 0;
    }

    /* A arte já tem formato de balão com fundo transparente: sem recorte redondo */
    .chat-ia-avatar {
      width: 36px;
      height: 36px;
      flex-shrink: 0;
    }

    .chat-ia-header-text {
      min-width: 0;
    }

    .chat-ia-header-text h2 {
      margin: 0;
      font-size: 1rem;
      font-weight: 600;
      line-height: 1.2;
    }

    .status-online {
      font-size: 0.75rem;
      color: var(--chat-ok);
    }

    .chat-ia-header-actions {
      display: flex;
      flex-shrink: 0;
    }

    .btn-header {
      position: relative;
      width: 44px;
      height: 44px;
      border: none;
      border-radius: 50%;
      background: transparent;
      color: var(--chat-text-2);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: background 0.2s ease, color 0.2s ease;
    }

    .btn-header:active,
    .btn-header.ativo {
      background: rgba(255, 255, 255, 0.08);
      color: var(--chat-text);
    }

    @media (hover: hover) {
      .btn-header:hover {
        background: rgba(255, 255, 255, 0.08);
        color: var(--chat-text);
      }
    }

    .btn-carrinho-header {
      color: var(--chat-text);
    }

    .carrinho-badge {
      position: absolute;
      top: 4px;
      right: 2px;
      min-width: 18px;
      height: 18px;
      padding: 0 4px;
      border-radius: 9px;
      background: var(--chat-accent-strong);
      color: #fff;
      font-size: 0.65rem;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
      border: 2px solid var(--chat-surface);
      animation: popIn 0.3s ease;
    }

    @keyframes popIn {
      0% { transform: scale(0); }
      50% { transform: scale(1.3); }
      100% { transform: scale(1); }
    }

    /* Animação de bounce quando adiciona item */
    .btn-carrinho-header.bounce {
      animation: cartBounce 0.6s ease;
    }

    @keyframes cartBounce {
      0%, 100% { transform: scale(1); }
      20% { transform: scale(1.2) rotate(-5deg); }
      40% { transform: scale(1.3) rotate(5deg); }
      60% { transform: scale(1.2) rotate(-3deg); }
      80% { transform: scale(1.1) rotate(2deg); }
    }

    /* Mensagens */
    .chat-ia-messages {
      flex: 1;
      min-height: 0;
      overflow-y: auto;
      padding: 1rem 0.75rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      background: var(--chat-bg);
      -webkit-overflow-scrolling: touch;
      overscroll-behavior: contain;
      scroll-behavior: smooth;
    }

    .chat-ia-message {
      display: flex;
      gap: 0.5rem;
      max-width: 90%;
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
      width: 28px;
      height: 28px;
      flex-shrink: 0;
    }

    .message-content {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      min-width: 0;
    }

    .message-bubble {
      padding: 0.625rem 0.875rem;
      border-radius: 18px;
      overflow-wrap: anywhere;
    }

    .chat-ia-message.user .message-bubble {
      background: var(--chat-accent-strong);
      color: #fff;
      border-bottom-right-radius: 6px;
    }

    .chat-ia-message.assistant .message-bubble {
      background: var(--chat-bubble);
      color: var(--chat-text);
      border-bottom-left-radius: 6px;
    }

    .message-text {
      margin: 0;
      font-size: 0.9375rem;
      line-height: 1.45;
      white-space: pre-wrap;
    }

    /* Resposta da IA: quebras vêm do <p>/<br> gerado pelo markdown */
    .message-text.markdown {
      white-space: normal;
    }

    .message-text.markdown ::ng-deep p,
    .message-text.markdown ::ng-deep ul,
    .message-text.markdown ::ng-deep ol {
      margin: 0 0 0.5rem;
    }

    .message-text.markdown ::ng-deep :last-child {
      margin-bottom: 0;
    }

    .message-text.markdown ::ng-deep ul,
    .message-text.markdown ::ng-deep ol {
      padding-left: 1.25rem;
    }

    .message-text.markdown ::ng-deep li + li {
      margin-top: 0.25rem;
    }

    .message-text.markdown ::ng-deep strong {
      font-weight: 700;
      color: #fff;
    }

    .message-time {
      display: block;
      margin-top: 0.25rem;
      font-size: 0.6875rem;
      text-align: right;
      color: var(--chat-text-3);
    }

    .chat-ia-message.user .message-time {
      color: rgba(255, 255, 255, 0.8);
    }

    /* Digitando */
    .message-bubble.typing {
      padding: 0.875rem 1.125rem;
      background: var(--chat-bubble);
      border-bottom-left-radius: 6px;
    }

    .typing-indicator {
      display: flex;
      gap: 4px;
    }

    .typing-indicator span {
      width: 8px;
      height: 8px;
      background: var(--chat-text-3);
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

    /* Sugestões iniciais, alinhadas com as bolhas (avatar 28px + gap 8px) */
    .sugestoes {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      padding-left: 36px;
      animation: fadeIn 0.3s ease;
    }

    .sugestao-chip {
      min-height: 44px;
      padding: 0.5rem 1rem;
      border: 1px solid rgba(255, 154, 107, 0.45);
      border-radius: 999px;
      background: rgba(255, 107, 53, 0.08);
      color: var(--chat-accent-soft);
      font-size: 0.875rem;
      font-weight: 500;
      cursor: pointer;
      transition: background 0.2s ease;
    }

    .sugestao-chip:active {
      background: rgba(255, 107, 53, 0.2);
    }

    /* Produtos destacados: um carrossel por categoria */
    .produtos-destacados {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .categoria-grupo {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      min-width: 0;
    }

    .categoria-titulo {
      margin: 0;
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--chat-text-3);
    }

    .categoria-cards {
      display: flex;
      gap: 0.5rem;
      overflow-x: auto;
      scroll-snap-type: x mandatory;
      overscroll-behavior-x: contain;
      scrollbar-width: none;
    }

    .categoria-cards::-webkit-scrollbar {
      display: none;
    }

    .produto-card {
      flex: 0 0 140px;
      scroll-snap-align: start;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      padding: 0.5rem;
      background: var(--chat-bubble);
      border: 1px solid var(--chat-border);
      border-radius: 14px;
      cursor: pointer;
      transition: transform 0.15s ease, border-color 0.2s ease;
    }

    .produto-card:active {
      transform: scale(0.97);
    }

    @media (hover: hover) {
      .produto-card:hover {
        border-color: rgba(255, 107, 53, 0.45);
      }
    }

    .produto-card.indisponivel {
      opacity: 0.55;
      cursor: not-allowed;
    }

    .produto-imagem,
    .produto-imagem-placeholder {
      width: 100%;
      height: 88px;
      border-radius: 10px;
      object-fit: cover;
      background: var(--chat-bg);
    }

    .produto-imagem-placeholder {
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.75rem;
    }

    .produto-nome {
      margin: 0.25rem 0 0;
      font-size: 0.8125rem;
      font-weight: 600;
      line-height: 1.25;
      color: var(--chat-text);
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .produto-descricao {
      margin: 0;
      font-size: 0.75rem;
      line-height: 1.3;
      color: var(--chat-text-2);
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .produto-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.25rem;
      margin-top: auto;
      padding-top: 0.25rem;
    }

    .produto-preco {
      font-size: 0.875rem;
      font-weight: 700;
      color: var(--chat-ok);
      white-space: nowrap;
    }

    .btn-adicionar {
      width: 40px;
      height: 40px;
      flex-shrink: 0;
      border: none;
      border-radius: 50%;
      background: var(--chat-accent-strong);
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: transform 0.15s ease;
    }

    .btn-adicionar svg {
      width: 18px;
      height: 18px;
      stroke-width: 2.5;
    }

    .btn-adicionar:active {
      transform: scale(0.9);
    }

    .produto-indisponivel {
      font-size: 0.7rem;
      font-weight: 600;
      color: var(--chat-danger);
    }

    /* Input */
    .chat-ia-input-area {
      position: relative;
      z-index: 10;
      flex-shrink: 0;
      padding: 0.625rem 0.75rem;
      padding-bottom: max(0.625rem, env(safe-area-inset-bottom));
      background: var(--chat-surface);
      border-top: 1px solid var(--chat-border);
    }

    .input-wrapper {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .chat-ia-input {
      flex: 1;
      min-width: 0;
      height: 44px;
      padding: 0 1rem;
      border: 1px solid transparent;
      border-radius: 22px;
      background: var(--chat-bubble);
      color: var(--chat-text);
      font-family: inherit;
      /* 16px no mínimo evita o zoom automático do iOS */
      font-size: 16px;
      outline: none;
      -webkit-appearance: none;
      appearance: none;
      transition: border-color 0.2s ease;
    }

    .chat-ia-input:focus {
      border-color: var(--chat-accent);
    }

    .chat-ia-input::placeholder {
      color: var(--chat-text-3);
    }

    .chat-ia-input:disabled {
      opacity: 0.6;
    }

    .btn-enviar {
      width: 44px;
      height: 44px;
      flex-shrink: 0;
      border: none;
      border-radius: 50%;
      background: var(--chat-accent-strong);
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: background 0.2s ease, transform 0.15s ease;
    }

    .btn-enviar svg {
      width: 20px;
      height: 20px;
      stroke-width: 2.5;
    }

    .btn-enviar:active:not(:disabled) {
      transform: scale(0.92);
    }

    /* Vazio: neutro, e não um laranja apagado que parece quebrado */
    .btn-enviar:disabled {
      background: rgba(255, 255, 255, 0.08);
      color: var(--chat-text-3);
      cursor: default;
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

    /* Histórico: gaveta inferior com fundo escurecido */
    .historico-backdrop {
      position: absolute;
      inset: 0;
      z-index: 20;
      background: rgba(0, 0, 0, 0.5);
      animation: fade 0.2s ease;
    }

    @keyframes fade {
      from { opacity: 0; }
    }

    .historico-panel {
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 21;
      max-height: 70%;
      display: flex;
      flex-direction: column;
      background: var(--chat-surface);
      border-radius: 20px 20px 0 0;
      padding-bottom: env(safe-area-inset-bottom, 0px);
      animation: slideUp 0.25s ease;
    }

    @keyframes slideUp {
      from { transform: translateY(100%); }
      to { transform: translateY(0); }
    }

    .historico-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-shrink: 0;
      padding: 0.5rem 0.25rem 0.5rem 1.25rem;
      border-bottom: 1px solid var(--chat-border);
    }

    .historico-header h3 {
      margin: 0;
      font-size: 1rem;
      font-weight: 600;
      color: var(--chat-text);
    }

    .historico-lista {
      flex: 1;
      min-height: 0;
      overflow-y: auto;
      overscroll-behavior: contain;
      padding: 0.5rem 0.75rem 0.75rem;
    }

    .historico-vazio {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 2rem;
      color: var(--chat-text-3);
    }

    .historico-vazio span {
      font-size: 2rem;
      margin-bottom: 0.5rem;
    }

    .historico-vazio p {
      margin: 0;
      font-size: 0.875rem;
    }

    .historico-item {
      position: relative;
      margin-bottom: 0.5rem;
      padding: 0.75rem 3rem 0.75rem 0.875rem;
      border-radius: 12px;
      background: var(--chat-bg);
      cursor: pointer;
      transition: background 0.2s ease;
    }

    .historico-item:active {
      background: var(--chat-bubble);
    }

    @media (hover: hover) {
      .historico-item:hover {
        background: var(--chat-bubble);
      }
    }

    .historico-item-titulo,
    .historico-item-preview {
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .historico-item-titulo {
      margin-bottom: 0.125rem;
      font-size: 0.875rem;
      font-weight: 600;
      color: var(--chat-text);
    }

    .historico-item-preview {
      margin-bottom: 0.25rem;
      font-size: 0.8125rem;
      color: var(--chat-text-2);
    }

    .historico-item-data {
      font-size: 0.75rem;
      color: var(--chat-text-3);
    }

    .btn-remover-conversa {
      position: absolute;
      top: 50%;
      right: 0.25rem;
      transform: translateY(-50%);
      width: 44px;
      height: 44px;
      border: none;
      border-radius: 50%;
      background: transparent;
      color: var(--chat-text-3);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
    }

    .btn-remover-conversa svg {
      width: 18px;
      height: 18px;
    }

    .btn-remover-conversa:active {
      color: var(--chat-danger);
    }

    /* 320px com carrinho: 4 botões de 44px não deixam espaço para o nome */
    @media (max-width: 359px) {
      .chat-ia-avatar {
        display: none;
      }
    }
  `]
})
export class ChatIAFullscreenComponent implements AfterViewChecked {
  // Inputs
  readonly isOpen = input.required<boolean>();
  /** Esconde temporariamente o chat (ex: quando modal de produto abre) */
  readonly isHidden = input<boolean>(false);
  readonly isLoading = input<boolean>(false);
  readonly inputText = input<string>('');
  readonly canSend = input<boolean>(false);
  readonly mensagens = input<MensagemChat[]>([]);
  /** Quantidade de itens no carrinho para exibir badge */
  readonly quantidadeItensCarrinho = input<number>(0);
  /** Flag para disparar animação no carrinho (quando item é adicionado) */
  readonly animarCarrinho = input<boolean>(false);
  /** Lista de conversas anteriores */
  readonly historicoConversas = input<ConversaSalva[]>([]);
  /** Se deve mostrar o painel de histórico */
  readonly mostrarHistorico = input<boolean>(false);

  // Outputs
  readonly onClose = output<void>();
  readonly onSend = output<void>();
  readonly onInputChange = output<string>();
  readonly onNovaConversa = output<void>();
  /** Emitido quando o usuário clica em "Adicionar" em um card de produto */
  readonly onAdicionarProduto = output<ProdutoDestacado>();
  /** Emitido quando o usuário clica no carrinho no header */
  readonly onAbrirCarrinho = output<void>();
  /** Emitido para alternar exibição do histórico */
  readonly onToggleHistorico = output<void>();
  /** Emitido quando o usuário seleciona uma conversa do histórico */
  readonly onCarregarConversa = output<string>();
  /** Emitido quando o usuário remove uma conversa do histórico */
  readonly onRemoverConversa = output<string>();

  @ViewChild('messagesContainer') private readonly messagesContainer?: ElementRef<HTMLDivElement>;
  @ViewChild('chatInput') private readonly chatInput?: ElementRef<HTMLInputElement>;
  @ViewChild('inputArea') private readonly inputArea?: ElementRef<HTMLElement>;

  /** Perguntas prontas para quem não sabe por onde começar */
  readonly sugestoes = ['O que você recomenda?', 'Tem opção sem carne?', 'Quero algo para beber'];
  readonly mostrarSugestoes = computed(() =>
    !this.isLoading() && !this.mensagens().some(m => m.from === 'user')
  );

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

  formatarPreco(valor: number): string {
    return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  formatTime(date: Date): string {
    return date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  }

  formatDate(date: Date): string {
    const hoje = new Date();
    const ontem = new Date(hoje);
    ontem.setDate(ontem.getDate() - 1);

    if (date.toDateString() === hoje.toDateString()) {
      return `Hoje às ${this.formatTime(date)}`;
    } else if (date.toDateString() === ontem.toDateString()) {
      return `Ontem às ${this.formatTime(date)}`;
    } else {
      return date.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }) + ` às ${this.formatTime(date)}`;
    }
  }

  enviar(): void {
    if (this.canSend()) {
      this.onSend.emit();
    }
  }

  /** Reusa o fluxo normal: preenche o input e envia (o pai valida texto e loading). */
  enviarSugestao(texto: string): void {
    this.onInputChange.emit(texto);
    this.onSend.emit();
  }

  /**
   * Adiciona um produto ao carrinho emitindo evento para o componente pai.
   */
  adicionarAoCarrinho(produto: ProdutoDestacado): void {
    this.onAdicionarProduto.emit(produto);
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

  /**
   * Chamado quando o input recebe foco.
   * Garante que a área de input fique visível quando o teclado virtual abre no mobile.
   */
  onInputFocus(): void {
    // Pequeno delay para aguardar o teclado virtual aparecer
    setTimeout(() => {
      // Scroll das mensagens para o final
      this.scrollToBottom();

      // Garante que a área de input esteja visível usando scrollIntoView
      if (this.inputArea?.nativeElement) {
        this.inputArea.nativeElement.scrollIntoView({
          behavior: 'smooth',
          block: 'end'
        });
      }

      // Fallback: usa window.scrollTo se necessário (para alguns navegadores mobile)
      if (typeof globalThis !== 'undefined' && 'visualViewport' in globalThis) {
        const visualViewport = globalThis.visualViewport;
        if (visualViewport) {
          // Força reflow do layout quando teclado abre
          setTimeout(() => {
            this.scrollToBottom();
          }, 100);
        }
      }
    }, 150);
  }

  /**
   * Agrupa produtos por categoria para exibição organizada.
   */
  agruparPorCategoria(produtos: ProdutoDestacado[]): { nome: string; produtos: ProdutoDestacado[] }[] {
    const grupos = new Map<string, ProdutoDestacado[]>();

    for (const produto of produtos) {
      const categoria = produto.categoria || 'Outros';
      if (!grupos.has(categoria)) {
        grupos.set(categoria, []);
      }
      grupos.get(categoria)!.push(produto);
    }

    return Array.from(grupos.entries()).map(([nome, produtos]) => ({ nome, produtos }));
  }
}
