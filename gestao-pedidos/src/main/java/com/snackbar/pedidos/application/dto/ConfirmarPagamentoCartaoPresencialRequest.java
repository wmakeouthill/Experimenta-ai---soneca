package com.snackbar.pedidos.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConfirmarPagamentoCartaoPresencialRequest(
        @NotBlank String correlationId,
        @NotNull Boolean aprovado,
        String nsuTef,
        String bandeira,
        String codigoAutorizacao,
        String codigoAdquirente,
        String comprovanteCliente,
        String motivo) {
}
