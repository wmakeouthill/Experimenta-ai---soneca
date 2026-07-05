package com.snackbar.pedidos.infrastructure.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Component;

import com.snackbar.pedidos.infrastructure.config.PagamentoProperties;

import lombok.RequiredArgsConstructor;

/**
 * Autentica webhooks da Getnet pelo header X-Webhook-Token
 * (token compartilhado cadastrado no portal Getnet), com comparacao
 * em tempo constante. Sem token configurado, TODO webhook e rejeitado.
 */
@Component
@RequiredArgsConstructor
public class GetnetWebhookVerifier {

    private final PagamentoProperties properties;

    public boolean tokenValido(String tokenRecebido) {
        String esperado = properties.getGetnet().getWebhookToken();
        if (esperado == null || esperado.isBlank()
                || tokenRecebido == null || tokenRecebido.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                tokenRecebido.trim().getBytes(StandardCharsets.UTF_8));
    }
}
