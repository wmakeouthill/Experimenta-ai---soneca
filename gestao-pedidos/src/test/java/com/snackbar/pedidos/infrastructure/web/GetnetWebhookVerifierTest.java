package com.snackbar.pedidos.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.snackbar.pedidos.infrastructure.config.PagamentoProperties;

class GetnetWebhookVerifierTest {

    private GetnetWebhookVerifier verifierComToken(String tokenConfigurado) {
        PagamentoProperties properties = new PagamentoProperties();
        properties.getGetnet().setWebhookToken(tokenConfigurado);
        return new GetnetWebhookVerifier(properties);
    }

    @Test
    void deveAceitarQuandoTokenConfere() {
        assertTrue(verifierComToken("segredo-123").tokenValido("segredo-123"));
    }

    @Test
    void deveRejeitarQuandoTokenDiferente() {
        assertFalse(verifierComToken("segredo-123").tokenValido("errado"));
    }

    @Test
    void deveRejeitarQuandoTokenAusente() {
        assertFalse(verifierComToken("segredo-123").tokenValido(null));
        assertFalse(verifierComToken("segredo-123").tokenValido(""));
    }

    @Test
    void deveRejeitarQuandoNaoHaTokenConfigurado() {
        assertFalse(verifierComToken(null).tokenValido("qualquer"));
        assertFalse(verifierComToken("").tokenValido("qualquer"));
    }
}
