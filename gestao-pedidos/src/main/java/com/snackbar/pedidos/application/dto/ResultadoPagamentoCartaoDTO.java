package com.snackbar.pedidos.application.dto;

public record ResultadoPagamentoCartaoDTO(boolean aprovado, String motivo, PedidoPendenteDTO pedido) {
}
