package com.snackbar.pedidos.infrastructure.gateway;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
import com.snackbar.pedidos.application.ports.PixGatewayPort;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.infrastructure.config.PagamentoProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Gateway PIX real da Getnet (QR Code dinamico).
 * Ativo com pagamento.gateway.pix=GETNET.
 * txid do dominio = payment_id da Getnet.
 */
@Component
@ConditionalOnProperty(name = "pagamento.gateway.pix", havingValue = "GETNET")
@Slf4j
public class GetnetPixAdapter implements PixGatewayPort {

    private final RestClient restClient;
    private final GetnetAuthClient authClient;
    private final PagamentoProperties properties;
    private final QrCodePngGenerator qrCodePngGenerator;

    public GetnetPixAdapter(
            RestClient.Builder restClientBuilder,
            GetnetAuthClient authClient,
            PagamentoProperties properties,
            QrCodePngGenerator qrCodePngGenerator) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getGetnet().getBaseUrl())
                .build();
        this.authClient = authClient;
        this.properties = properties;
        this.qrCodePngGenerator = qrCodePngGenerator;
    }

    @Override
    public GatewayPagamento gateway() {
        return GatewayPagamento.GETNET;
    }

    @Override
    public CobrancaPixCriada criarCobrancaDinamica(CriarCobrancaPixCommand command) {
        Map<String, Object> body = Map.of(
                "amount", command.valorCentavos(),
                "currency", "BRL",
                "order_id", command.referenciaPedido(),
                "customer_id", command.correlationId());

        QrCodeResponse response = executarComRetryDeToken(() -> restClient.post()
                .uri("/v1/payments/qrcode/pix")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.obterToken())
                .header("seller_id", properties.getGetnet().getSellerId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(QrCodeResponse.class));

        if (response == null || response.paymentId() == null
                || response.additionalData() == null
                || response.additionalData().qrCode() == null) {
            throw new ValidationException("resposta invalida do gateway Getnet ao criar cobranca PIX");
        }

        String copiaECola = response.additionalData().qrCode();
        LocalDateTime expiracao = parseExpiracao(response.additionalData().expirationDateQrcode());

        log.info("Cobranca PIX Getnet criada paymentId={} status={}",
                response.paymentId(), response.status());

        return new CobrancaPixCriada(
                response.paymentId(),
                copiaECola,
                qrCodePngGenerator.gerarDataUri(copiaECola),
                copiaECola,
                expiracao);
    }

    @Override
    public ResultadoConsultaPix consultarStatus(String txid) {
        ConsultaResponse response = executarComRetryDeToken(() -> restClient.get()
                .uri("/v1/payments/qrcode/{paymentId}", txid)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.obterToken())
                .header("seller_id", properties.getGetnet().getSellerId())
                .retrieve()
                .body(ConsultaResponse.class));

        if (response == null) {
            return new ResultadoConsultaPix(StatusCobrancaPix.DESCONHECIDO, null);
        }
        return new ResultadoConsultaPix(mapearStatus(response.status()), null);
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

    private LocalDateTime parseExpiracao(String valor) {
        if (valor != null && !valor.isBlank()) {
            try {
                return OffsetDateTime.parse(valor).toLocalDateTime();
            } catch (Exception ignored) {
                // tenta o proximo formato
            }
            try {
                return LocalDateTime.parse(valor);
            } catch (Exception exception) {
                log.warn("Nao foi possivel interpretar expiration_date_qrcode='{}'; usando default", valor);
            }
        }
        return LocalDateTime.now().plusSeconds(properties.getPix().getExpiracaoSegundos());
    }

    private StatusCobrancaPix mapearStatus(String status) {
        if (status == null) {
            return StatusCobrancaPix.DESCONHECIDO;
        }
        return switch (status.toUpperCase()) {
            case "PAID", "APPROVED", "CONFIRMED" -> StatusCobrancaPix.PAGA;
            case "WAITING", "PENDING", "CREATED", "AUTHORIZED" -> StatusCobrancaPix.AGUARDANDO;
            case "EXPIRED" -> StatusCobrancaPix.EXPIRADA;
            case "CANCELED", "CANCELLED", "DENIED", "ERROR" -> StatusCobrancaPix.CANCELADA;
            default -> StatusCobrancaPix.DESCONHECIDO;
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QrCodeResponse(
            @JsonProperty("payment_id") String paymentId,
            String status,
            @JsonProperty("additional_data") AdditionalData additionalData) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record AdditionalData(
                @JsonProperty("transaction_id") String transactionId,
                @JsonProperty("qr_code") String qrCode,
                @JsonProperty("expiration_date_qrcode") String expirationDateQrcode) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConsultaResponse(
            @JsonProperty("payment_id") String paymentId,
            String status) {
    }
}
