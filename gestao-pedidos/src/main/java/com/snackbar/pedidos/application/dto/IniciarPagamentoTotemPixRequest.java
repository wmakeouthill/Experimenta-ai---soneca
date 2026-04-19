package com.snackbar.pedidos.application.dto;

import jakarta.validation.constraints.NotBlank;

public record IniciarPagamentoTotemPixRequest(
        @NotBlank String pedidoId,
        @NotBlank String correlationId) {
}
