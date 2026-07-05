package com.snackbar.pedidos.infrastructure.gateway;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.snackbar.kernel.domain.exceptions.ValidationException;

/**
 * Gera a imagem PNG (data URI base64) de um payload PIX copia-e-cola.
 * Usado quando o gateway devolve apenas o texto EMV do QR Code.
 */
@Component
public class QrCodePngGenerator {

    private static final int TAMANHO_PX = 320;
    private static final String PREFIXO_DATA_URI = "data:image/png;base64,";

    public String gerarDataUri(String conteudo) {
        if (conteudo == null || conteudo.isBlank()) {
            throw new ValidationException("conteudo do QR Code e obrigatorio");
        }
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                    conteudo,
                    BarcodeFormat.QR_CODE,
                    TAMANHO_PX,
                    TAMANHO_PX,
                    Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.CHARACTER_SET, "UTF-8"));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return PREFIXO_DATA_URI + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception exception) {
            throw new ValidationException("falha ao gerar imagem do QR Code");
        }
    }
}
