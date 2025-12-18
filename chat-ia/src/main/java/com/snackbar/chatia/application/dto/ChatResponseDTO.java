package com.snackbar.chatia.application.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO para resposta do chat IA.
 * Suporta respostas simples de texto e respostas ricas com cards de produtos.
 */
public record ChatResponseDTO(
    String reply,
    List<ProdutoDestacadoDTO> produtosDestacados
) {
    
    /**
     * Produto destacado para exibição como card no chat.
     */
    public record ProdutoDestacadoDTO(
        String id,
        String nome,
        String descricao,
        String categoria,
        BigDecimal preco,
        String imagemUrl,
        boolean disponivel
    ) {}
    
    /**
     * Cria resposta simples apenas com texto.
     */
    public static ChatResponseDTO de(String reply) {
        return new ChatResponseDTO(reply, List.of());
    }
    
    /**
     * Cria resposta com texto e produtos destacados.
     */
    public static ChatResponseDTO comProdutos(String reply, List<ProdutoDestacadoDTO> produtos) {
        return new ChatResponseDTO(reply, produtos);
    }
    
    /**
     * Cria resposta de erro.
     */
    public static ChatResponseDTO erro(String mensagemErro) {
        return new ChatResponseDTO(mensagemErro, List.of());
    }
}
