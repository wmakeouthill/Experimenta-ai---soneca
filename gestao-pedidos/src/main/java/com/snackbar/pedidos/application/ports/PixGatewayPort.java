package com.snackbar.pedidos.application.ports;

import java.time.LocalDateTime;

import com.snackbar.pedidos.domain.entities.GatewayPagamento;

/**
 * Porta de gateway PIX. Implementacoes: SimuladoPixAdapter (default),
 * GetnetPixAdapter (pagamento.gateway.pix=GETNET). Stone: futuro.
 */
public interface PixGatewayPort {

    /** Identifica qual gateway esta ativo (gravado no Pagamento). */
    GatewayPagamento gateway();

    CobrancaPixCriada criarCobrancaDinamica(CriarCobrancaPixCommand command);

    /**
     * Consulta o status da cobranca no gateway (fallback quando o webhook
     * nao chega). txid = identificador da cobranca no gateway
     * (payment_id na Getnet).
     */
    ResultadoConsultaPix consultarStatus(String txid);

    enum StatusCobrancaPix {
        AGUARDANDO,
        PAGA,
        EXPIRADA,
        CANCELADA,
        DESCONHECIDO
    }

    record CriarCobrancaPixCommand(
            String referenciaPedido,
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

    record ResultadoConsultaPix(
            StatusCobrancaPix status,
            String endToEndId) {
    }
}
