package com.snackbar.pedidos.domain.entities;

public enum ModoPagamentoMesa {
    /** Cliente paga ao fazer o pedido; pedido so entra na fila apos aprovacao. */
    PRE_PAGO,
    /** Cliente consome e fecha a conta no final. */
    POS_PAGO
}
