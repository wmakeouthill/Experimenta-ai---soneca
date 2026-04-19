package com.snackbar.pedidos.application.dto;

import com.snackbar.pedidos.domain.entities.MeioPagamentoTotem;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IniciarPagamentoTotemCartaoRequest(
        @NotBlank String pedidoId,
        @NotNull MeioPagamentoTotem meioPagamento,
        @NotBlank String correlationId) {
}
