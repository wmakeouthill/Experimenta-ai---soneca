package com.snackbar.pedidos.application.dto;

import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IniciarPagamentoCartaoPresencialRequest(
        @NotBlank String pedidoId,
        @NotNull MeioPagamentoGateway meioPagamento,
        @NotBlank String correlationId) {
}
