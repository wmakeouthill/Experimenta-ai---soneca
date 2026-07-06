package com.snackbar.pedidos.application.dto;

import java.util.List;

public record ContaMesaDTO(
        String id,
        int numeroMesa,
        String status,
        long valorCentavos,
        List<ItemContaDTO> pedidos) {

    public record ItemContaDTO(String pedidoId, String numeroPedido, long valorCentavos) {
    }
}
