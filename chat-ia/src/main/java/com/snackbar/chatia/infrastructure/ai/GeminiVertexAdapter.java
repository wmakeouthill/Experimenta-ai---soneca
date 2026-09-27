package com.snackbar.chatia.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.snackbar.chatia.application.port.out.IAClientPort;
import com.snackbar.chatia.domain.entity.MensagemChat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter para o Gemini no Vertex AI (generateContent via REST).
 * Tenta os modelos em ordem (principal + fallbacks) até um responder.
 * Autentica com a conta de serviço via Application Default Credentials.
 * Instanciado em ChatIAConfig.
 */
@Slf4j
@RequiredArgsConstructor
public class GeminiVertexAdapter implements IAClientPort {

    static final String RESPOSTA_INDISPONIVEL =
            "Desculpe, o assistente está indisponível no momento. Tente novamente em instantes. 🙏";

    // gemini-3.8-flash rejeita MINIMAL; LOW é o menor nível aceito pelos dois modelos
    private static final String NIVEL_RACIOCINIO = "LOW";
    private static final int TIMEOUT_SEGUNDOS = 60;
    private static final String KEY_TEXT = "text";
    private static final String KEY_PARTS = "parts";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    /** Nulo quando o Vertex AI não está configurado. */
    private final GoogleCredentials credenciais;
    private final String projetoId;
    private final String localizacao;
    private final List<String> modelos;
    private final int maxTokens;

    @Override
    public String chat(String systemPrompt, List<MensagemChat> historico, String mensagemAtual) {
        if (credenciais == null) {
            log.warn("Chat IA sem credenciais do Vertex AI - verifique GEMINI_PROJECT_ID e GOOGLE_APPLICATION_CREDENTIALS");
            return RESPOSTA_INDISPONIVEL;
        }

        Map<String, Object> payload = criarPayload(systemPrompt, historico, mensagemAtual);
        Exception ultimoErro = null;
        for (String modelo : modelos) {
            try {
                String resposta = gerarConteudo(modelo, payload);
                log.info("Resposta obtida com modelo {}", modelo);
                return resposta;
            } catch (IOException e) {
                ultimoErro = e;
                log.warn("Modelo {} falhou: {}. Tentando próximo...", modelo, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ultimoErro = e;
                break;
            }
        }

        // Detalhe só no log: o texto volta para o cliente e fica no histórico da conversa
        log.error("Nenhum modelo do Vertex AI respondeu ({})", modelos, ultimoErro);
        return RESPOSTA_INDISPONIVEL;
    }

    private Map<String, Object> criarPayload(String systemPrompt, List<MensagemChat> historico, String mensagemAtual) {
        List<Map<String, Object>> conteudos = new ArrayList<>();
        for (MensagemChat msg : historico) {
            conteudos.add(conteudo(msg.isAssistant() ? "model" : "user", msg.content()));
        }
        conteudos.add(conteudo("user", mensagemAtual));

        return Map.of(
                "systemInstruction", Map.of(KEY_PARTS, List.of(Map.of(KEY_TEXT, systemPrompt))),
                "contents", conteudos,
                // Sem temperature: nos Gemini 3 o Google recomenda o padrão (1.0); abaixo disso o modelo pode entrar em loop
                "generationConfig", Map.of(
                        "maxOutputTokens", maxTokens,
                        "thinkingConfig", Map.of("thinkingLevel", NIVEL_RACIOCINIO)));
    }

    private static Map<String, Object> conteudo(String papel, String texto) {
        return Map.of("role", papel, KEY_PARTS, List.of(Map.of(KEY_TEXT, texto)));
    }

    private String gerarConteudo(String modelo, Map<String, Object> payload) throws IOException, InterruptedException {
        URI uri = URI.create(urlModelo(modelo));
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(TIMEOUT_SEGUNDOS))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
        // Authorization (renova o token quando expira) e x-goog-user-project, se houver
        credenciais.getRequestMetadata(uri)
                .forEach((nome, valores) -> valores.forEach(valor -> requisicao.header(nome, valor)));

        HttpResponse<String> resposta = httpClient.send(requisicao.build(), HttpResponse.BodyHandlers.ofString());
        if (resposta.statusCode() != 200) {
            throw new IOException("status " + resposta.statusCode() + ": " + extrairMensagemErro(resposta.body()));
        }
        return extrairTexto(resposta.body());
    }

    private String urlModelo(String modelo) {
        // O endpoint global não leva a região no host
        String host = "global".equals(localizacao) ? "aiplatform.googleapis.com" : localizacao + "-aiplatform.googleapis.com";
        return "https://%s/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent"
                .formatted(host, projetoId, localizacao, modelo);
    }

    private String extrairTexto(String corpo) throws IOException {
        JsonNode candidato = objectMapper.readTree(corpo).path("candidates").path(0);
        StringBuilder texto = new StringBuilder();
        for (JsonNode parte : candidato.path("content").path(KEY_PARTS)) {
            if (!parte.path("thought").asBoolean(false)) {
                texto.append(parte.path(KEY_TEXT).asText(""));
            }
        }
        if (texto.isEmpty()) {
            throw new IOException("resposta sem texto (finishReason=" + candidato.path("finishReason").asText("?") + ")");
        }
        return texto.toString();
    }

    private String extrairMensagemErro(String corpo) {
        try {
            return objectMapper.readTree(corpo).path("error").path("message").asText("Erro desconhecido");
        } catch (IOException e) {
            return "Erro desconhecido";
        }
    }
}
