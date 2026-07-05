package com.snackbar.pedidos.infrastructure.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.infrastructure.config.PagamentoProperties;

/**
 * Autenticacao OAuth client_credentials da Getnet, com cache do
 * access_token (renova 60s antes de expirar).
 */
@Component
public class GetnetAuthClient {

    private static final long MARGEM_RENOVACAO_SEGUNDOS = 60;

    private final RestClient restClient;
    private final PagamentoProperties properties;
    private final AtomicReference<TokenCache> cache = new AtomicReference<>();

    public GetnetAuthClient(RestClient.Builder restClientBuilder, PagamentoProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.getGetnet().getBaseUrl())
                .build();
    }

    public String obterToken() {
        TokenCache atual = cache.get();
        if (atual != null && Instant.now().isBefore(atual.expiraEm())) {
            return atual.token();
        }
        return autenticar();
    }

    public void invalidar() {
        cache.set(null);
    }

    private synchronized String autenticar() {
        TokenCache atual = cache.get();
        if (atual != null && Instant.now().isBefore(atual.expiraEm())) {
            return atual.token();
        }

        String credenciais = properties.getGetnet().getClientId() + ":"
                + properties.getGetnet().getClientSecret();
        String basic = Base64.getEncoder()
                .encodeToString(credenciais.getBytes(StandardCharsets.UTF_8));

        TokenResponse response = restClient.post()
                .uri("/auth/oauth/v2/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("scope=oob&grant_type=client_credentials")
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new ValidationException("resposta de autenticacao invalida do gateway Getnet");
        }

        long validadeSegundos = Math.max(response.expiresIn() - MARGEM_RENOVACAO_SEGUNDOS, 30);
        cache.set(new TokenCache(response.accessToken(), Instant.now().plusSeconds(validadeSegundos)));
        return response.accessToken();
    }

    private record TokenCache(String token, Instant expiraEm) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {
    }
}
