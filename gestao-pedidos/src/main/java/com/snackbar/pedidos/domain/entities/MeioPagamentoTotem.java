package com.snackbar.pedidos.domain.entities;

public enum MeioPagamentoTotem {
    CARTAO_CREDITO,
    CARTAO_DEBITO,
    CARTAO_VOUCHER,
    PIX;

    public boolean isCartao() {
        return this == CARTAO_CREDITO || this == CARTAO_DEBITO || this == CARTAO_VOUCHER;
    }
}
