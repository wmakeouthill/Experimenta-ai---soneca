package com.snackbar.pedidos.application.ports;

import java.time.LocalDateTime;

public interface StonePixGatewayPort {

    CobrancaPixCriada criarCobrancaDinamica(CriarCobrancaPixCommand command);

    record CriarCobrancaPixCommand(
            String pedidoId,
            String correlationId,
            long valorCentavos) {
    }

    record CobrancaPixCriada(
            String txid,
            String qrCodePayload,
            String qrCodeBase64,
            String copiaECola,
            LocalDateTime expiracaoEm) {
    }
}
