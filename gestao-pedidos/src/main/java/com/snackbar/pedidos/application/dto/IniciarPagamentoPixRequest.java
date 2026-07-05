package com.snackbar.pedidos.application.dto;

import jakarta.validation.constraints.NotBlank;

public record IniciarPagamentoPixRequest(
        @NotBlank String pedidoId,
        @NotBlank String correlationId) {
}
