package com.snackbar.chatia.application.port.out;

import com.snackbar.chatia.domain.entity.MensagemChat;
import java.util.List;

/**
 * Porta de saída para comunicação com a API de IA (OpenAI).
 */
public interface IAClientPort {
    
    /**
     * Envia mensagens para a API de IA e retorna a resposta.
     * 
     * @param systemPrompt instruções do sistema para a IA
     * @param historico histórico de mensagens anteriores
     * @param mensagemAtual mensagem atual do usuário
     * @return resposta da IA
     */
    String chat(String systemPrompt, List<MensagemChat> historico, String mensagemAtual);
}
