package com.snackbar.chatia.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.auth.oauth2.GoogleCredentials;
import com.snackbar.chatia.application.port.out.IAClientPort;
import com.snackbar.chatia.infrastructure.ai.GeminiVertexAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Configuração do módulo Chat IA.
 * Define beans para ObjectMapper, HttpClient e o cliente do Gemini (Vertex AI).
 */
@Slf4j
@Configuration
@ComponentScan(basePackages = "com.snackbar.chatia")
public class ChatIAConfig {

    private static final int DEFAULT_TIMEOUT_SEGUNDOS = 60;
    private static final String ESCOPO_CLOUD_PLATFORM = "https://www.googleapis.com/auth/cloud-platform";

    /**
     * Bean do ObjectMapper configurado para o módulo Chat IA.
     * Reutiliza configuração padrão se já existir, senão cria uma nova.
     */
    @Bean(name = "chatIAObjectMapper")
    public ObjectMapper chatIAObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Bean do HttpClient para chamadas ao Vertex AI.
     */
    @Bean(name = "chatIAHttpClient")
    public HttpClient chatIAHttpClient(
            @Value("${gemini.timeout.seconds:" + DEFAULT_TIMEOUT_SEGUNDOS + "}") int timeoutSegundos) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSegundos))
                .build();
    }

    /**
     * Cliente do Gemini. Sem projeto ou credenciais, a aplicação sobe e o chat responde "indisponível".
     */
    @Bean
    public IAClientPort iaClientPort(
            @Qualifier("chatIAHttpClient") HttpClient httpClient,
            @Qualifier("chatIAObjectMapper") ObjectMapper objectMapper,
            @Value("${gemini.project-id:}") String projetoId,
            @Value("${gemini.location:global}") String localizacao,
            @Value("${gemini.model:gemini-3.8-flash}") String modeloPrincipal,
            @Value("${gemini.models.fallback:gemini-3.5-flash-lite}") String modelosFallback,
            @Value("${gemini.max-tokens:4000}") int maxTokens) {
        List<String> modelos = Stream.concat(Stream.of(modeloPrincipal), Arrays.stream(modelosFallback.split(",")))
                .map(String::trim)
                .filter(modelo -> !modelo.isBlank())
                .distinct()
                .toList();
        GoogleCredentials credenciais = carregarCredenciais(projetoId);

        log.info("Gemini (Vertex AI) - projeto: {}, local: {}, modelos: {}, max_tokens: {}, credenciais: {}",
                projetoId, localizacao, modelos, maxTokens, credenciais != null ? "ok" : "AUSENTES");
        return new GeminiVertexAdapter(httpClient, objectMapper, credenciais, projetoId, localizacao, modelos, maxTokens);
    }

    private static GoogleCredentials carregarCredenciais(String projetoId) {
        if (projetoId.isBlank()) {
            log.warn("GEMINI_PROJECT_ID não definido - Chat IA ficará indisponível");
            return null;
        }
        try {
            return GoogleCredentials.getApplicationDefault().createScoped(ESCOPO_CLOUD_PLATFORM);
        } catch (IOException e) {
            log.warn("Credenciais do Google não encontradas (GOOGLE_APPLICATION_CREDENTIALS) - Chat IA ficará indisponível: {}",
                    e.getMessage());
            return null;
        }
    }
}
