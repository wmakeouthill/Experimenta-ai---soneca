package com.snackbar.pedidos.domain.entities;

public enum StatusPedido {
    PENDENTE("Pendente"),
    PREPARANDO("Preparando"),
    PRONTO("Pronto"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");
    
    private final String descricao;
    
    StatusPedido(String descricao) {
        this.descricao = descricao;
    }
    
    public String getDescricao() {
        return descricao;
    }
    
    public boolean podeSerAtualizadoPara(StatusPedido novoStatus) {
        // Permite mudanças reversas para flexibilidade operacional
        // Apenas CANCELADO não pode ser alterado (regra de negócio)
        if (this == CANCELADO) {
            return false;
        }
        
        // Permite todas as outras transições, incluindo voltar de FINALIZADO
        return true;
    }
}

