package com.snackbar.pedidos.infrastructure.gateway;

import java.util.Map;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.infrastructure.config.PagamentoProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Gateway Getnet de cartao digital: tokeniza o PAN e cobra no credito.
 * Ativo com pagamento.gateway.cartao-digital=GETNET.
 */
@Component
@ConditionalOnProperty(name = "pagamento.gateway.cartao-digital", havingValue = "GETNET")
@Slf4j
public class GetnetCartaoDigitalAdapter implements CartaoDigitalGatewayPort {

    private final RestClient restClient;
    private final GetnetAuthClient authClient;
    private final PagamentoProperties properties;

    public GetnetCartaoDigitalAdapter(
            RestClient.Builder restClientBuilder,
            GetnetAuthClient authClient,
            PagamentoProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getGetnet().getBaseUrl())
                .build();
        this.authClient = authClient;
        this.properties = properties;
    }

    @Override
    public GatewayPagamento gateway() {
        return GatewayPagamento.GETNET;
    }

    @Override
    public ResultadoPagamentoCartao pagar(PagarCartaoCommand command) {
        String numberToken = tokenizar(command.cartao().numero());
        PagamentoCartaoResponse response = cobrar(command, numberToken);

        if (response == null || response.status() == null || response.status().isBlank()) {
            throw new ValidationException("resposta invalida do gateway Getnet ao cobrar cartao digital");
        }

        boolean aprovado = "APPROVED".equalsIgnoreCase(response.status());
        CreditResponse credit = response.credit();
        String motivo = aprovado ? null : motivoRecusa(response);

        log.info("Pagamento cartao Getnet paymentId={} status={} ref={}",
                response.paymentId(), response.status(), command.referencia());

        return new ResultadoPagamentoCartao(
                aprovado,
                response.paymentId(),
                credit != null ? credit.brand() : null,
                credit != null ? credit.authorizationCode() : null,
                motivo);
    }

    private String tokenizar(String numeroCartao) {
        TokenCartaoResponse response = executarComRetryDeToken(() -> restClient.post()
                .uri("/v1/tokens/card")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.obterToken())
                .header("seller_id", properties.getGetnet().getSellerId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("card_number", numeroCartao))
                .retrieve()
                .body(TokenCartaoResponse.class));

        if (response == null || response.numberToken() == null || response.numberToken().isBlank()) {
            throw new ValidationException("resposta invalida do gateway Getnet ao tokenizar cartao");
        }
        return response.numberToken();
    }

    private PagamentoCartaoResponse cobrar(PagarCartaoCommand command, String numberToken) {
        return executarComRetryDeToken(() -> restClient.post()
                .uri("/v1/payments/credit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.obterToken())
                .header("seller_id", properties.getGetnet().getSellerId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpoCobranca(command, numberToken))
                .retrieve()
                .body(PagamentoCartaoResponse.class));
    }

    private Map<String, Object> corpoCobranca(PagarCartaoCommand command, String numberToken) {
        return Map.of(
                "seller_id", properties.getGetnet().getSellerId(),
                "amount", command.valorCentavos(),
                "currency", "BRL",
                "order", Map.of("order_id", command.referencia()),
                "customer", Map.of("customer_id", command.correlationId()),
                "credit", Map.of(
                        "delayed", false,
                        "authenticated", false,
                        "pre_authorization", false,
                        "save_card_data", false,
                        "transaction_type", "FULL",
                        "number_installments", command.parcelas(),
                        "card", Map.of(
                                "number_token", numberToken,
                                "cardholder_name", command.cartao().nomePortador(),
                                "expiration_month", command.cartao().validadeMes(),
                                "expiration_year", command.cartao().validadeAno(),
                                "security_code", command.cartao().cvv(),
                                "brand", "")));
    }

    private <T> T executarComRetryDeToken(Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (HttpClientErrorException.Unauthorized exception) {
            log.info("Token Getnet rejeitado (401); renovando e repetindo a chamada");
            authClient.invalidar();
            return chamada.get();
        }
    }

    private String motivoRecusa(PagamentoCartaoResponse response) {
        CreditResponse credit = response.credit();
        if (credit != null && credit.reasonMessage() != null && !credit.reasonMessage().isBlank()) {
            return credit.reasonMessage();
        }
        return "Pagamento nao aprovado: " + response.status();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenCartaoResponse(@JsonProperty("number_token") String numberToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PagamentoCartaoResponse(
            @JsonProperty("payment_id") String paymentId,
            String status,
            CreditResponse credit) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CreditResponse(
            @JsonProperty("authorization_code") String authorizationCode,
            String brand,
            @JsonProperty("reason_message") String reasonMessage) {
    }
}
