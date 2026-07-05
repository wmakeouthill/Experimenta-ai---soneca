package com.snackbar.pedidos.infrastructure.gateway;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.snackbar.pedidos.application.ports.PixGatewayPort;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;

/**
 * Gateway PIX simulado para desenvolvimento e demonstracao.
 * Ativo quando pagamento.gateway.pix=SIMULADO (default).
 * A "aprovacao" acontece via webhook simulado
 * (POST /api/v1/webhooks/stone/pix disparado pelo frontend).
 */
@Component
@ConditionalOnProperty(name = "pagamento.gateway.pix", havingValue = "SIMULADO", matchIfMissing = true)
public class SimuladoPixAdapter implements PixGatewayPort {

    private final int expiracaoSegundos;

    public SimuladoPixAdapter(@Value("${pagamento.pix.expiracao-segundos:180}") int expiracaoSegundos) {
        this.expiracaoSegundos = expiracaoSegundos;
    }

    @Override
    public GatewayPagamento gateway() {
        return GatewayPagamento.SIMULADO;
    }

    @Override
    public CobrancaPixCriada criarCobrancaDinamica(CriarCobrancaPixCommand command) {
        String txid = "SIM" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String copiaECola = "PIX_SIMULADO|" + command.correlationId() + "|" + command.valorCentavos();
        String qrCodeBase64 = gerarQrCodeSimulado(txid, command.valorCentavos());

        return new CobrancaPixCriada(
                txid,
                copiaECola,
                qrCodeBase64,
                copiaECola,
                LocalDateTime.now().plusSeconds(expiracaoSegundos));
    }

    @Override
    public ResultadoConsultaPix consultarStatus(String txid) {
        // O simulador nao tem backend de consulta: aprovacao chega apenas
        // pelo webhook simulado.
        return new ResultadoConsultaPix(StatusCobrancaPix.AGUARDANDO, null);
    }

    private String gerarQrCodeSimulado(String txid, long valorCentavos) {
        String valor = "R$ " + (valorCentavos / 100) + "," + String.format("%02d", valorCentavos % 100);
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="320" height="320">
                  <rect width="320" height="320" fill="#ffffff"/>
                  <rect x="24" y="24" width="272" height="272" fill="#111111"/>
                  <rect x="48" y="48" width="224" height="224" fill="#ffffff"/>
                  <text x="160" y="132" text-anchor="middle" font-family="Arial" font-size="24" fill="#111111">PIX</text>
                  <text x="160" y="168" text-anchor="middle" font-family="Arial" font-size="18" fill="#111111">SIMULADO</text>
                  <text x="160" y="202" text-anchor="middle" font-family="Arial" font-size="16" fill="#111111">%s</text>
                  <text x="160" y="238" text-anchor="middle" font-family="Arial" font-size="10" fill="#111111">%s</text>
                </svg>
                """.formatted(valor, txid);
        return "data:image/svg+xml;base64," + Base64.getEncoder()
                .encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }
}
