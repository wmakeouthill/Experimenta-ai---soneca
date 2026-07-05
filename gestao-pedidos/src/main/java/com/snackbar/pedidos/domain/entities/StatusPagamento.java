package com.snackbar.pedidos.domain.entities;

public enum StatusPagamento {
    INICIADO,
    AGUARDANDO_TEF,
    AGUARDANDO_PIX,
    APROVADO,
    NEGADO,
    CANCELADO,
    FALHA_TECNICA,
    EXPIRADO
}
