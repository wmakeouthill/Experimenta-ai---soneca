package com.snackbar.pedidos.infrastructure.web;

import java.util.Iterator;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.usecases.ConfirmarPagamentoPixUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
public class WebhookStonePixController {

    private final StonePixWebhookVerifier verifier;
    private final ConfirmarPagamentoPixUseCase confirmarPixUseCase;
    private final ObjectMapper objectMapper;

    @PostMapping("/api/v1/webhooks/stone/pix")
    public ResponseEntity<Map<String, String>> receberWebhookPix(
            @RequestHeader(value = "X-Stone-Signature", required = false) String assinatura,
            @RequestBody String payload) {

        if (!verifier.assinaturaValida(payload, assinatura)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "assinatura_invalida"));
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String txid = buscarTexto(root, "txid");
            String endToEndId = buscarTexto(root, "endToEndId", "end_to_end_id", "e2eId");

            if (txid == null || endToEndId == null) {
                log.warn("Webhook PIX recebido sem txid/endToEndId");
                return ResponseEntity.ok(Map.of("status", "ignorado"));
            }

            confirmarPixUseCase.executar(txid, endToEndId);
            return ResponseEntity.ok(Map.of("status", "processado"));
        } catch (ValidationException exception) {
            log.warn("Webhook PIX ignorado: {}", exception.getMessage());
            return ResponseEntity.ok(Map.of("status", "ignorado"));
        } catch (Exception exception) {
            log.warn("Webhook PIX invalido: {}", exception.getMessage());
            return ResponseEntity.ok(Map.of("status", "ignorado"));
        }
    }

    private String buscarTexto(JsonNode node, String... nomes) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String nome : nomes) {
            JsonNode valor = node.get(nome);
            if (valor != null && valor.isTextual() && !valor.asText().isBlank()) {
                return valor.asText();
            }
        }

        if (node.isObject()) {
            Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
                String encontrado = buscarTexto(children.next(), nomes);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                String encontrado = buscarTexto(child, nomes);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }

        return null;
    }
}
