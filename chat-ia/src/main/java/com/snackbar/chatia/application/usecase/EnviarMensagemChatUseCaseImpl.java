package com.snackbar.chatia.application.usecase;

import com.snackbar.chatia.application.dto.ChatRequestDTO;
import com.snackbar.chatia.application.dto.ChatResponseDTO;
import com.snackbar.chatia.application.port.in.EnviarMensagemChatUseCase;
import com.snackbar.chatia.application.port.out.IAClientPort;
import com.snackbar.chatia.domain.entity.MensagemChat;
import com.snackbar.chatia.domain.repository.HistoricoChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Caso de uso para enviar mensagens ao chat IA.
 * Orquestra a comunicação com a IA e gerenciamento do histórico.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnviarMensagemChatUseCaseImpl implements EnviarMensagemChatUseCase {
    
    private final IAClientPort iaClient;
    private final HistoricoChatRepository historicoRepository;
    
    @Value("${chat.ia.system-prompt:Você é o Soneca, um assistente virtual simpático e prestativo de uma lanchonete. Ajude os clientes com dúvidas sobre o cardápio, pedidos e funcionamento do estabelecimento. Seja amigável, use emojis ocasionalmente e mantenha respostas concisas.}")
    private String systemPrompt;
    
    @Override
    public ChatResponseDTO executar(ChatRequestDTO request) {
        String sessionId = request.sessionId();
        String mensagemUsuario = request.message();
        
        log.info("Processando mensagem do chat - Session: {}", sessionId);
        
        try {
            // Obtém histórico da sessão
            List<MensagemChat> historico = historicoRepository.obterHistorico(sessionId);
            
            // Adiciona mensagem do usuário ao histórico
            MensagemChat msgUsuario = MensagemChat.doUsuario(mensagemUsuario);
            historicoRepository.adicionarMensagem(sessionId, msgUsuario);
            
            // Chama a IA
            String respostaIA = iaClient.chat(systemPrompt, historico, mensagemUsuario);
            
            // Adiciona resposta da IA ao histórico
            MensagemChat msgAssistente = MensagemChat.doAssistente(respostaIA);
            historicoRepository.adicionarMensagem(sessionId, msgAssistente);
            
            log.info("Resposta do chat gerada com sucesso - Session: {}", sessionId);
            return ChatResponseDTO.de(respostaIA);
            
        } catch (Exception e) {
            log.error("Erro ao processar mensagem do chat - Session: {}", sessionId, e);
            return ChatResponseDTO.erro("Desculpe, ocorreu um erro ao processar sua mensagem. Tente novamente.");
        }
    }
}
