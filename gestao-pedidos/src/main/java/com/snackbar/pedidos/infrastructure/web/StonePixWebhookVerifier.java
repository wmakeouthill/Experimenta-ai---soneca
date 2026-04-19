package com.snackbar.pedidos.infrastructure.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.snackbar.pedidos.infrastructure.config.PagamentoTotemProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StonePixWebhookVerifier {

    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final PagamentoTotemProperties properties;

    public boolean assinaturaValida(String payload, String assinaturaRecebida) {
        String secret = properties.getPix().getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            return properties.getPix().isMockEnabled();
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
