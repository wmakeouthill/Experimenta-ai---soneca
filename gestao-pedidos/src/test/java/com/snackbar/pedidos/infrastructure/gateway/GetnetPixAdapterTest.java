package com.snackbar.pedidos.infrastructure.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.snackbar.pedidos.application.ports.PixGatewayPort.CriarCobrancaPixCommand;
import com.snackbar.pedidos.application.ports.PixGatewayPort.StatusCobrancaPix;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.infrastructure.config.PagamentoProperties;

class GetnetPixAdapterTest {

    private static final String BASE_URL = "https://api-homologacao.getnet.com.br";
    private static final String TOKEN_JSON =
            "{\"access_token\":\"token-abc\",\"expires_in\":3600}";

    private MockRestServiceServer server;
    private GetnetPixAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        PagamentoProperties properties = new PagamentoProperties();
        properties.getGetnet().setBaseUrl(BASE_URL);
        properties.getGetnet().setClientId("client-id");
        properties.getGetnet().setClientSecret("client-secret");
        properties.getGetnet().setSellerId("seller-1");
        properties.getPix().setExpiracaoSegundos(180);

        GetnetAuthClient authClient = new GetnetAuthClient(builder, properties);
        adapter = new GetnetPixAdapter(builder, authClient, properties, new QrCodePngGenerator());
    }

    private void esperarAutenticacao() {
        server.expect(requestTo(BASE_URL + "/auth/oauth/v2/token"))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
    }

    @Test
    void deveIdentificarGatewayGetnet() {
        assertEquals(GatewayPagamento.GETNET, adapter.gateway());
    }

    @Test
    void deveCriarCobrancaPixComHeadersECorpoCorretos() {
        esperarAutenticacao();
        server.expect(requestTo(BASE_URL + "/v1/payments/qrcode/pix"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-abc"))
                .andExpect(header("seller_id", "seller-1"))
                .andExpect(jsonPath("$.amount").value(2500))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.order_id").value("pedido-1"))
                .andExpect(jsonPath("$.customer_id").value("corr-1"))
                .andRespond(withSuccess("""
                        {
                          "payment_id": "pay-123",
                          "status": "WAITING",
                          "additional_data": {
                            "transaction_id": "trx-1",
                            "qr_code": "00020126PIXCOPIAECOLA",
                            "expiration_date_qrcode": "2026-07-05T21:30:00"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var cobranca = adapter.criarCobrancaDinamica(
                new CriarCobrancaPixCommand("pedido-1", "corr-1", 2500L));

        assertEquals("pay-123", cobranca.txid());
        assertEquals("00020126PIXCOPIAECOLA", cobranca.copiaECola());
        assertTrue(cobranca.qrCodeBase64().startsWith("data:image/png;base64,"));
        assertNotNull(cobranca.expiracaoEm());
        server.verify();
    }

    @Test
    void deveConsultarStatusEMapearComoPaga() {
        esperarAutenticacao();
        server.expect(requestTo(BASE_URL + "/v1/payments/qrcode/pay-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"payment_id\":\"pay-123\",\"status\":\"PAID\"}",
                        MediaType.APPLICATION_JSON));

        var resultado = adapter.consultarStatus("pay-123");

        assertEquals(StatusCobrancaPix.PAGA, resultado.status());
        server.verify();
    }

    @Test
    void deveRenovarTokenERepetirChamadaQuando401() {
        esperarAutenticacao();
        server.expect(requestTo(BASE_URL + "/v1/payments/qrcode/pay-123"))
                .andRespond(withUnauthorizedRequest());
        esperarAutenticacao();
        server.expect(requestTo(BASE_URL + "/v1/payments/qrcode/pay-123"))
                .andRespond(withSuccess(
                        "{\"payment_id\":\"pay-123\",\"status\":\"EXPIRED\"}",
                        MediaType.APPLICATION_JSON));

        var resultado = adapter.consultarStatus("pay-123");

        assertEquals(StatusCobrancaPix.EXPIRADA, resultado.status());
        server.verify();
    }
}
