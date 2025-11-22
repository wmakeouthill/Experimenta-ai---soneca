package com.snackbar.pedidos.domain.entities;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import lombok.Getter;

@Getter
public class ItemPedido {
    private String produtoId;
    private String produtoNome;
    private int quantidade;
    private Preco precoUnitario;
    private String observacoes;
    
    private ItemPedido() {
    }
    
    public static ItemPedido criar(String produtoId, String produtoNome, int quantidade, Preco precoUnitario, String observacoes) {
        validarDados(produtoId, produtoNome, quantidade, precoUnitario);
        
        ItemPedido item = new ItemPedido();
        item.produtoId = produtoId;
        item.produtoNome = produtoNome;
        item.quantidade = quantidade;
        item.precoUnitario = precoUnitario;
        item.observacoes = observacoes != null ? observacoes.trim() : null;
        return item;
    }
    
    public Preco calcularSubtotal() {
        return precoUnitario.multiply(quantidade);
    }
    
    public void atualizarQuantidade(int novaQuantidade) {
        if (novaQuantidade <= 0) {
            throw new ValidationException("Quantidade deve ser maior que zero");
        }
        this.quantidade = novaQuantidade;
    }
    
    public void atualizarObservacoes(String novasObservacoes) {
        this.observacoes = novasObservacoes != null ? novasObservacoes.trim() : null;
    }
    
    private static void validarDados(String produtoId, String produtoNome, int quantidade, Preco precoUnitario) {
        if (produtoId == null || produtoId.trim().isEmpty()) {
            throw new ValidationException("ID do produto não pode ser nulo ou vazio");
        }
        if (produtoNome == null || produtoNome.trim().isEmpty()) {
            throw new ValidationException("Nome do produto não pode ser nulo ou vazio");
        }
        if (quantidade <= 0) {
            throw new ValidationException("Quantidade deve ser maior que zero");
        }
        if (precoUnitario == null) {
            throw new ValidationException("Preço unitário não pode ser nulo");
        }
    }
}

