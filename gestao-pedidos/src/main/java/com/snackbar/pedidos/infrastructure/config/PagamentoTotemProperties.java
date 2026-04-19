package com.snackbar.pedidos.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "pagamento.totem")
@Getter
@Setter
public class PagamentoTotemProperties {

    private boolean enabled = false;
    private Pix pix = new Pix();

    @Getter
    @Setter
    public static class Pix {
        private boolean mockEnabled = false;
        private String baseUrl = "https://api.openbank.stone.com.br";
        private String clientId;
        private String clientSecret;
        private String accountId;
        private String webhookSecret;
        private int expiracaoSegundos = 300;
    }
}
