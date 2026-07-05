package com.snackbar.pedidos.infrastructure.web;

import java.util.Iterator;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.usecases.ConfirmarPagamentoPixUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Recebe notificacoes de pagamento PIX da Getnet.
 * Sempre responde 200 apos autenticar (excecoes internas nao viram 5xx,
 * para a Getnet nao suspender as notificacoes); casos nao processaveis
 * retornam {status: ignorado}.
 */
@RestController
@RequestMapping("/api/v1/webhooks/getnet")
@RequiredArgsConstructor
@Slf4j
public class WebhookGetnetPixController {

    private final ConfirmarPagamentoPixUseCase confirmarPixUseCase;
    private final GetnetWebhookVerifier verifier;
    private final ObjectMapper objectMapper;

    @PostMapping("/pix")
    public ResponseEntity<Map<String, String>> receber(
            @RequestHeader(value = "X-Webhook-Token", required = false) String token,
            @RequestBody String payload) {

        if (!verifier.tokenValido(token)) {
            log.warn("Webhook Getnet rejeitado: token invalido ou ausente");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String paymentId = buscarTexto(root, "payment_id", "paymentId");
            String status = buscarTexto(root, "status");

            if (paymentId == null || paymentId.isBlank()) {
                log.warn("Webhook Getnet sem payment_id; ignorado");
                return ResponseEntity.ok(Map.of("status", "ignorado"));
            }
            if (!statusAprovado(status)) {
                log.info("Webhook Getnet paymentId={} status={} nao e aprovacao; ignorado",
                        paymentId, status);
                return ResponseEntity.ok(Map.of("status", "ignorado"));
            }

            String endToEndId = buscarTexto(root, "end_to_end_id", "endToEndId", "e2eId");
            confirmarPixUseCase.executar(
                    paymentId,
                    endToEndId != null && !endToEndId.isBlank() ? endToEndId : "GETNET-" + paymentId);

            log.info("Webhook Getnet processado paymentId={}", paymentId);
            return ResponseEntity.ok(Map.of("status", "processado"));

        } catch (ValidationException exception) {
            log.warn("Webhook Getnet ignorado: {}", exception.getMessage());
            return ResponseEntity.ok(Map.of("status", "ignorado"));
        } catch (Exception exception) {
            log.error("Erro ao processar webhook Getnet: {}", exception.getMessage());
            return ResponseEntity.ok(Map.of("status", "ignorado"));
        }
    }

    private boolean statusAprovado(String status) {
        if (status == null) {
            return false;
        }
        return switch (status.toUpperCase()) {
            case "PAID", "APPROVED", "CONFIRMED" -> true;
            default -> false;
        };
    }

    /** Busca em profundidade pelo primeiro campo textual com um dos nomes dados. */
    private String buscarTexto(JsonNode node, String... nomes) {
        if (node == null) {
            return null;
        }
        for (String nome : nomes) {
            JsonNode encontrado = node.get(nome);
            if (encontrado != null && encontrado.isValueNode()) {
                return encontrado.asText();
            }
        }
        Iterator<JsonNode> filhos = node.elements();
        while (filhos.hasNext()) {
            JsonNode filho = filhos.next();
            if (filho.isContainerNode()) {
                String valor = buscarTexto(filho, nomes);
                if (valor != null) {
                    return valor;
                }
            }
        }
        return null;
    }
}
