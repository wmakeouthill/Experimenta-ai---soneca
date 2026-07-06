package com.snackbar.pedidos.application.dto;

/**
 * Resposta da criacao de um pedido-mesa pre-pago: o pedido pendente (oculto ate
 * o pagamento) e os dados da cobranca PIX para o cliente pagar.
 */
public record PedidoMesaComPixDTO(PedidoPendenteDTO pedido, PixCobrancaCriadaDTO pagamento) {
}
