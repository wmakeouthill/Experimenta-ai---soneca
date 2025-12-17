import { signal, computed, inject } from '@angular/core';
import { ChatIAService, ChatIAResponse } from '../../../services/chat-ia.service';

export interface MensagemChat {
    id: string;
    from: 'user' | 'assistant';
    text: string;
    timestamp: Date;
}

const SESSION_KEY = 'chat-ia-session-id';
const MENSAGENS_KEY = 'chat-ia-mensagens';

/**
 * Gera ou recupera o sessionId do sessionStorage.
 */
function obterOuGerarSessionId(): string {
    let sessionId = sessionStorage.getItem(SESSION_KEY);
    if (!sessionId) {
        sessionId = crypto.randomUUID();
        sessionStorage.setItem(SESSION_KEY, sessionId);
    }
    return sessionId;
}

/**
 * Salva mensagens no sessionStorage.
 */
function salvarMensagens(mensagens: MensagemChat[]): void {
    const simplificadas = mensagens.map(m => ({
        id: m.id,
        from: m.from,
        text: m.text,
        timestamp: m.timestamp.toISOString()
    }));
    sessionStorage.setItem(MENSAGENS_KEY, JSON.stringify(simplificadas));
}

/**
 * Carrega mensagens do sessionStorage.
 */
function carregarMensagens(): MensagemChat[] {
    const saved = sessionStorage.getItem(MENSAGENS_KEY);
    if (!saved) return [];

    try {
        const parsed = JSON.parse(saved);
        return parsed.map((m: { id: string; from: 'user' | 'assistant'; text: string; timestamp: string }) => ({
            id: m.id,
            from: m.from,
            text: m.text,
            timestamp: new Date(m.timestamp)
        }));
    } catch {
        return [];
    }
}

/**
 * Composable para gerenciar o estado do Chat IA.
 * Fornece estado reativo e métodos para interação com o chat.
 */
export function useChatIA() {
    const chatService = inject(ChatIAService);

    // Estado
    const isOpen = signal(false);
    const isLoading = signal(false);
    const inputText = signal('');
    const mensagens = signal<MensagemChat[]>([]);
    const erro = signal<string | null>(null);

    let sessionId = obterOuGerarSessionId();

    // Computed
    const canSend = computed(() => inputText().trim().length > 0 && !isLoading());
    const carrinhoVazio = computed(() => mensagens().length <= 1); // Só mensagem inicial

    // Mensagem inicial de boas-vindas
    const mensagemInicial: MensagemChat = {
        id: 'initial',
        from: 'assistant',
        text: 'Olá! 👋 Sou o Soneca, seu assistente virtual. Como posso ajudar você hoje? Pode me perguntar sobre o cardápio, fazer sugestões de pedidos ou tirar qualquer dúvida!',
        timestamp: new Date()
    };

    /**
     * Inicializa o chat carregando mensagens salvas ou mensagem inicial.
     */
    function inicializar(): void {
        const salvas = carregarMensagens();
        if (salvas.length > 0) {
            mensagens.set(salvas);
        } else {
            mensagens.set([mensagemInicial]);
        }
    }

    /**
     * Abre ou fecha o chat.
     */
    function toggleChat(): void {
        isOpen.update(v => !v);
        if (isOpen() && mensagens().length === 0) {
            mensagens.set([mensagemInicial]);
        }
    }

    /**
     * Abre o chat.
     */
    function abrirChat(): void {
        isOpen.set(true);
        if (mensagens().length === 0) {
            mensagens.set([mensagemInicial]);
        }
    }

    /**
     * Fecha o chat.
     */
    function fecharChat(): void {
        isOpen.set(false);
    }

    /**
     * Atualiza o texto de input.
     */
    function setInputText(value: string): void {
        inputText.set(value);
    }

    /**
     * Envia uma mensagem para o chat.
     */
    function enviarMensagem(): void {
        const text = inputText().trim();
        if (!text || isLoading()) return;

        // Adiciona mensagem do usuário
        const msgUsuario: MensagemChat = {
            id: crypto.randomUUID(),
            from: 'user',
            text,
            timestamp: new Date()
        };

        mensagens.update(msgs => [...msgs, msgUsuario]);
        inputText.set('');
        isLoading.set(true);
        erro.set(null);

        // Salva estado
        salvarMensagens(mensagens());

        // Envia para o backend
        chatService.enviarMensagem(text, sessionId).subscribe({
            next: (response: ChatIAResponse) => {
                const msgAssistente: MensagemChat = {
                    id: crypto.randomUUID(),
                    from: 'assistant',
                    text: response.reply,
                    timestamp: new Date()
                };
                mensagens.update(msgs => [...msgs, msgAssistente]);
                salvarMensagens(mensagens());
                isLoading.set(false);
            },
            error: () => {
                erro.set('Erro ao enviar mensagem. Tente novamente.');
                isLoading.set(false);
            }
        });
    }

    /**
     * Inicia uma nova conversa, limpando o histórico.
     */
    function novaConversa(): void {
        const sessionIdAntigo = sessionId;

        // Limpa frontend
        mensagens.set([mensagemInicial]);
        sessionStorage.removeItem(SESSION_KEY);
        sessionStorage.removeItem(MENSAGENS_KEY);

        // Gera novo sessionId
        sessionId = obterOuGerarSessionId();

        // Limpa backend
        chatService.limparHistorico(sessionIdAntigo).subscribe();

        salvarMensagens(mensagens());
    }

    return {
        // Estado
        isOpen,
        isLoading,
        inputText,
        mensagens,
        erro,

        // Computed
        canSend,
        carrinhoVazio,

        // Métodos
        inicializar,
        toggleChat,
        abrirChat,
        fecharChat,
        setInputText,
        enviarMensagem,
        novaConversa
    };
}

export type ChatIAComposable = ReturnType<typeof useChatIA>;
