package com.snackbar.pedidos.application.dto;

import java.math.BigDecimal;

/**
 * DTO para produtos populares (mais pedidos ou mais bem avaliados).
 */
public record ProdutoPopularDTO(
        String id,
        String nome,
        String descricao,
        BigDecimal preco,
        String foto,
        String categoriaNome,
        Long quantidadeVendida,
        Double mediaAvaliacao,
        Integer totalAvaliacoes) {
    /**
     * Construtor para produtos mais pedidos (sem avaliação)
     */
    public static ProdutoPopularDTO maisPedido(
            String id, String nome, String descricao, BigDecimal preco,
            String foto, String categoriaNome, Long quantidadeVendida) {
        return new ProdutoPopularDTO(id, nome, descricao, preco, foto, categoriaNome, quantidadeVendida, null, null);
    }

    /**
     * Construtor para produtos bem avaliados
     */
    public static ProdutoPopularDTO bemAvaliado(
            String id, String nome, String descricao, BigDecimal preco,
            String foto, String categoriaNome, Double mediaAvaliacao, Integer totalAvaliacoes) {
        return new ProdutoPopularDTO(id, nome, descricao, preco, foto, categoriaNome, null, mediaAvaliacao,
                totalAvaliacoes);
    }
}
