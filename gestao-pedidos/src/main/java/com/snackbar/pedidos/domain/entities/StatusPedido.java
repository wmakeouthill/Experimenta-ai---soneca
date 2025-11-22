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
        if (this == CANCELADO || this == FINALIZADO) {
            return false;
        }
        
        if (novoStatus == PENDENTE) {
            return false;
        }
        
        return true;
    }
}

