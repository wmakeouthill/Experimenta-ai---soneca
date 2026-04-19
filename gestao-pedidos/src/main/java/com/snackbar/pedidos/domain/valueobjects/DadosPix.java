package com.snackbar.pedidos.domain.valueobjects;

import java.time.LocalDateTime;

import com.snackbar.kernel.domain.exceptions.ValidationException;

public record DadosPix(
        String txid,
        String qrCodePayload,
        String qrCodeBase64,
        String copiaECola,
        LocalDateTime expiracaoEm) {

    public DadosPix {
        if (txid == null || txid.isBlank()) {
            throw new ValidationException("txid do PIX e obrigatorio");
        }
        if (qrCodePayload == null || qrCodePayload.isBlank()) {
            throw new ValidationException("payload do QR Code PIX e obrigatorio");
        }
        if (qrCodeBase64 == null || qrCodeBase64.isBlank()) {
            throw new ValidationException("imagem do QR Code PIX e obrigatoria");
        }
        if (copiaECola == null || copiaECola.isBlank()) {
            throw new ValidationException("copia e cola PIX e obrigatorio");
        }
        if (expiracaoEm == null) {
            throw new ValidationException("expiracao do PIX e obrigatoria");
        }
    }
}
