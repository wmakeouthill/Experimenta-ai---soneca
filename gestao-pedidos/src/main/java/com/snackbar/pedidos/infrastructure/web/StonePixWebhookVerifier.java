package com.snackbar.pedidos.infrastructure.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.infrastructure.config.PagamentoProperties;

import lombok.RequiredArgsConstructor;

/**
 * Verifica a assinatura HMAC do webhook PIX simulado/Stone.
 * Sem secret configurado, o webhook so e aceito quando o gateway
 * PIX ativo e o SIMULADO (uso em desenvolvimento).
 */
@Component
@RequiredArgsConstructor
public class StonePixWebhookVerifier {

    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final PagamentoProperties properties;

    public boolean assinaturaValida(String payload, String assinaturaRecebida) {
        String secret = properties.getSimulado().getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            return properties.getGateway().getPix() == GatewayPagamento.SIMULADO;
        }
        if (assinaturaRecebida == null || assinaturaRecebida.isBlank()) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            String assinaturaCalculada = toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            String normalizada = assinaturaRecebida.replace("sha256=", "").trim();
            return MessageDigest.isEqual(
                    assinaturaCalculada.getBytes(StandardCharsets.UTF_8),
                    normalizada.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            return false;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }
}
