package com.snackbar.pedidos.application.dto;

import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;

import jakarta.validation.constraints.NotNull;

public record ConfiguracaoPagamentoDTO(
        boolean pixTotemAtivo,
        boolean cartaoTotemAtivo,
        boolean pixMesaAtivo,
        boolean cartaoMesaAtivo,
        @NotNull ModoPagamentoMesa modoMesa) {
}
