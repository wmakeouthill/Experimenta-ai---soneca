package com.snackbar.pedidos.infrastructure.gateway;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.snackbar.kernel.domain.exceptions.ValidationException;

class QrCodePngGeneratorTest {

    private final QrCodePngGenerator generator = new QrCodePngGenerator();

    @Test
    void deveGerarDataUriPngValido() {
        String dataUri = generator.gerarDataUri(
                "00020126580014BR.GOV.BCB.PIX0136a1b2c3d4-e5f6-0000-0000-000000000000");

        assertTrue(dataUri.startsWith("data:image/png;base64,"));

        byte[] png = Base64.getDecoder().decode(dataUri.substring("data:image/png;base64,".length()));
        byte[] magicPng = {(byte) 0x89, 'P', 'N', 'G'};
        assertArrayEquals(magicPng, Arrays.copyOfRange(png, 0, 4));
    }

    @Test
    void deveLancarExcecaoQuandoConteudoVazio() {
        assertThrows(ValidationException.class, () -> generator.gerarDataUri(""));
        assertThrows(ValidationException.class, () -> generator.gerarDataUri(null));
    }
}
