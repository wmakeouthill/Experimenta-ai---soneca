package com.snackbar.pedidos.domain.valueobjects;

/**
 * Dados de retorno de uma cobranca de cartao digital aprovada pelo gateway.
 * Nao carrega PAN/CVV - apenas identificadores da transacao.
 */
public record DadosCartaoDigital(String gatewayPaymentId, String bandeira, String codigoAutorizacao) {
}
