package com.snackbar.chatia.application.dto;

/**
 * DTO para resposta do chat IA.
 */
public record ChatResponseDTO(String reply) {
    
    public static ChatResponseDTO de(String reply) {
        return new ChatResponseDTO(reply);
    }
    
    public static ChatResponseDTO erro(String mensagemErro) {
        return new ChatResponseDTO(mensagemErro);
    }
}
