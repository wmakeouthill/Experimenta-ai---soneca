package com.snackbar.pedidos.application.dto;

import java.time.LocalDateTime;

import com.snackbar.pedidos.domain.entities.PagamentoTotem;
import com.snackbar.pedidos.domain.entities.StatusPagamentoTotem;

public record PixCobrancaCriadaDTO(
        String correlationId,
        String txid,
        String qrCodePayload,
        String qrCodeBase64,
        String copiaECola,
        LocalDateTime expiracaoEm,
        StatusPagamentoTotem status) {

    public static PixCobrancaCriadaDTO de(PagamentoTotem pagamento) {
        return new PixCobrancaCriadaDTO(
                pagamento.getCorrelationId(),
                pagamento.getPixTxid(),
                pagamento.getPixQrCodePayload(),
                pagamento.getPixQrCodeBase64(),
                pagamento.getPixCopiaECola(),
                pagamento.getPixExpiracaoEm(),
                pagamento.getStatus());
    }
}
