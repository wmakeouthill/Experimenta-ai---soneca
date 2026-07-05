package com.snackbar.pedidos.infrastructure.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.snackbar.pedidos.infrastructure.config.PagamentoProperties;

class GetnetAuthClientTest {

    private static final String BASE_URL = "https://api-homologacao.getnet.com.br";
    private static final String TOKEN_URL = BASE_URL + "/auth/oauth/v2/token";

    private MockRestServiceServer server;
    private GetnetAuthClient authClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        PagamentoProperties properties = new PagamentoProperties();
        properties.getGetnet().setBaseUrl(BASE_URL);
        properties.getGetnet().setClientId("client-id");
        properties.getGetnet().setClientSecret("client-secret");

        authClient = new GetnetAuthClient(builder, properties);
    }

    @Test
    void deveAutenticarComBasicAuthEFormUrlEncoded() {
        String basicEsperado = "Basic " + Base64.getEncoder()
                .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", basicEsperado))
                .andExpect(content().string("scope=oob&grant_type=client_credentials"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token-abc\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        assertEquals("token-abc", authClient.obterToken());
        server.verify();
    }

    @Test
    void deveReutilizarTokenDoCacheDentroDaValidade() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token-abc\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        assertEquals("token-abc", authClient.obterToken());
        // segunda chamada NAO deve bater no servidor (nenhuma expectativa extra)
        assertEquals("token-abc", authClient.obterToken());
        server.verify();
    }

    @Test
    void deveReautenticarAposInvalidar() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token-1\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token-2\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        assertEquals("token-1", authClient.obterToken());
        authClient.invalidar();
        assertEquals("token-2", authClient.obterToken());
        server.verify();
    }
}
