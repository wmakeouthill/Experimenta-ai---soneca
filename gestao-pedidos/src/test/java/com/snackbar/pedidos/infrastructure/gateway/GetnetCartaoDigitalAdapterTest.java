package com.snackbar.pedidos.infrastructure.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort.DadosCartaoInput;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort.PagarCartaoCommand;
import com.snackbar.pedidos.infrastructure.config.PagamentoProperties;

class GetnetCartaoDigitalAdapterTest {

    private static final String BASE_URL = "https://api-homologacao.getnet.com.br";

    private MockRestServiceServer server;
    private GetnetCartaoDigitalAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        PagamentoProperties properties = new PagamentoProperties();
        properties.getGetnet().setBaseUrl(BASE_URL);
        properties.getGetnet().setSellerId("seller-1");
        properties.getGetnet().setClientId("cid");
        properties.getGetnet().setClientSecret("secret");

        GetnetAuthClient authClient = org.mockito.Mockito.mock(GetnetAuthClient.class);
        org.mockito.Mockito.when(authClient.obterToken()).thenReturn("token-abc");
        adapter = new GetnetCartaoDigitalAdapter(builder, authClient, properties);
    }

    @Test
    void deveTokenizarECobrarAprovado() {
        server.expect(requestTo(BASE_URL + "/v1/tokens/card"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-abc"))
                .andExpect(header("seller_id", "seller-1"))
                .andExpect(jsonPath("$.card_number").value("5155901222280001"))
                .andRespond(withSuccess("{\"number_token\":\"tok-999\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(BASE_URL + "/v1/payments/credit"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-abc"))
                .andExpect(header("seller_id", "seller-1"))
                .andExpect(jsonPath("$.seller_id").value("seller-1"))
                .andExpect(jsonPath("$.amount").value(3000))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.order.order_id").value("ref-1"))
                .andExpect(jsonPath("$.customer.customer_id").value("corr-1"))
                .andExpect(jsonPath("$.credit.card.number_token").value("tok-999"))
                .andExpect(jsonPath("$.credit.card.cardholder_name").value("ANA SILVA"))
                .andExpect(jsonPath("$.credit.card.expiration_month").value("12"))
                .andExpect(jsonPath("$.credit.card.expiration_year").value("2030"))
                .andRespond(withSuccess("""
                        {
                          "payment_id": "pay-123",
                          "status": "APPROVED",
                          "credit": {
                            "authorization_code": "auth-1",
                            "brand": "Mastercard"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var resultado = adapter.pagar(comando());

        assertTrue(resultado.aprovado());
        assertEquals("pay-123", resultado.gatewayPaymentId());
        assertEquals("Mastercard", resultado.bandeira());
        assertEquals("auth-1", resultado.codigoAutorizacao());
        server.verify();
    }

    @Test
    void deveMapearNegado() {
        server.expect(requestTo(BASE_URL + "/v1/tokens/card"))
                .andRespond(withSuccess("{\"number_token\":\"tok-999\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/v1/payments/credit"))
                .andRespond(withSuccess("""
                        {
                          "payment_id": "pay-456",
                          "status": "DENIED",
                          "credit": {
                            "reason_message": "Cartao recusado"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var resultado = adapter.pagar(comando());

        assertFalse(resultado.aprovado());
        assertEquals("pay-456", resultado.gatewayPaymentId());
        assertEquals("Cartao recusado", resultado.motivo());
        server.verify();
    }

    private PagarCartaoCommand comando() {
        return new PagarCartaoCommand("ref-1", "corr-1", 3000L,
                new DadosCartaoInput("5155901222280001", "ANA SILVA", "12", "2030", "123"), 1);
    }
}
