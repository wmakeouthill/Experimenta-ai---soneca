package com.snackbar.pedidos.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelarPagamentoRequest(
        @NotBlank String correlationId,
        String motivo) {
}
