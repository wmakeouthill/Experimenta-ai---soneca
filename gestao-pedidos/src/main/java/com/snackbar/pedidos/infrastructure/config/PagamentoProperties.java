package com.snackbar.pedidos.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.snackbar.pedidos.domain.entities.GatewayPagamento;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "pagamento")
@Getter
@Setter
public class PagamentoProperties {

    /** Master switch da feature de pagamentos integrados. */
    private boolean enabled = false;

    private Gateway gateway = new Gateway();
    private Pix pix = new Pix();
    private Getnet getnet = new Getnet();
    private Simulado simulado = new Simulado();

    @Getter
    @Setter
    public static class Gateway {
        /** Gateway usado para PIX digital. */
        private GatewayPagamento pix = GatewayPagamento.SIMULADO;
        /** Gateway usado para cartao digital de credito. */
        private GatewayPagamento cartaoDigital = GatewayPagamento.SIMULADO;
    }

    @Getter
    @Setter
    public static class Pix {
        /** Validade do QR Code em segundos (Getnet expira ~3min). */
        private int expiracaoSegundos = 180;
    }

    @Getter
    @Setter
    public static class Getnet {
        private String baseUrl = "https://api-homologacao.getnet.com.br";
        private String clientId;
        private String clientSecret;
        private String sellerId;
        /** Token compartilhado exigido no webhook (header X-Webhook-Token). */
        private String webhookToken;
    }

    @Getter
    @Setter
    public static class Simulado {
        /** Secret HMAC do webhook simulado/Stone; vazio = aceito so no gateway SIMULADO. */
        private String webhookSecret;
    }
}
