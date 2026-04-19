package com.snackbar.pedidos.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelarPagamentoTotemRequest(
        @NotBlank String correlationId,
        String motivo) {
}
