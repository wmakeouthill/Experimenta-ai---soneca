package com.snackbar.chatia.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.snackbar.chatia.domain.entity.MensagemChat;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiVertexAdapterTest {

    private static final String RESPOSTA_OK = """
            {"candidates":[{"content":{"role":"model","parts":[
              {"text":"pensando...","thought":true},
              {"text":"Temos X-Burger!"}
            ]},"finishReason":"STOP"}]}""";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = mock(HttpClient.class);
    private final GoogleCredentials credenciais = GoogleCredentials.create(
            new AccessToken("token-teste", new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))));

    private GeminiVertexAdapter adapter(GoogleCredentials credenciais) {
        return new GeminiVertexAdapter(httpClient, objectMapper, credenciais, "projeto-teste", "global",
                List.of("gemini-3.8-flash", "gemini-3.5-flash-lite"), 4000);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> resposta(int status, String corpo) {
        HttpResponse<String> resposta = mock(HttpResponse.class);
        when(resposta.statusCode()).thenReturn(status);
        when(resposta.body()).thenReturn(corpo);
        return resposta;
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveMontarRequisicaoNoFormatoDoVertex() throws Exception {
        // Arrange
        HttpResponse<String> ok = resposta(200, RESPOSTA_OK);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);
        List<MensagemChat> historico = List.of(MensagemChat.doUsuario("oi"), MensagemChat.doAssistente("Olá!"));

        // Act
        String texto = adapter(credenciais).chat("prompt do sistema", historico, "tem hambúrguer?");

        // Assert
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest requisicao = captor.getValue();
        assertThat(requisicao.uri()).hasToString("https://aiplatform.googleapis.com/v1/projects/projeto-teste"
                + "/locations/global/publishers/google/models/gemini-3.8-flash:generateContent");
        assertThat(requisicao.headers().firstValue("Authorization")).hasValue("Bearer token-teste");

        JsonNode corpo = objectMapper.readTree(lerCorpo(requisicao));
        assertThat(corpo.at("/systemInstruction/parts/0/text").asText()).isEqualTo("prompt do sistema");
        assertThat(corpo.path("contents").findValuesAsText("role")).containsExactly("user", "model", "user");
        assertThat(corpo.at("/contents/2/parts/0/text").asText()).isEqualTo("tem hambúrguer?");
        assertThat(corpo.at("/generationConfig/thinkingConfig/thinkingLevel").asText()).isEqualTo("LOW");
        assertThat(corpo.path("generationConfig").has("temperature")).isFalse();
        assertThat(texto).isEqualTo("Temos X-Burger!");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveUsarFallbackQuandoModeloPrincipalFalha() throws Exception {
        // Arrange
        HttpResponse<String> limite = resposta(429, "{\"error\":{\"message\":\"Resource exhausted\"}}");
        HttpResponse<String> ok = resposta(200, RESPOSTA_OK);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(limite, ok);

        // Act
        String texto = adapter(credenciais).chat("prompt", List.of(), "oi");

        // Assert
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getAllValues().get(1).uri().getPath()).endsWith("/models/gemini-3.5-flash-lite:generateContent");
        assertThat(texto).isEqualTo("Temos X-Burger!");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveTentarProximoModeloQuandoRespostaVemSemTexto() throws Exception {
        // Arrange
        HttpResponse<String> vazia = resposta(200, "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}");
        HttpResponse<String> ok = resposta(200, RESPOSTA_OK);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(vazia, ok);

        // Act
        String texto = adapter(credenciais).chat("prompt", List.of(), "oi");

        // Assert
        assertThat(texto).isEqualTo("Temos X-Burger!");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveResponderIndisponivelSemVazarErroQuandoTodosModelosFalham() throws Exception {
        // Arrange
        HttpResponse<String> erro = resposta(500, "{\"error\":{\"message\":\"detalhe interno\"}}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(erro);

        // Act
        String texto = adapter(credenciais).chat("prompt", List.of(), "oi");

        // Assert
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThat(texto).isEqualTo(GeminiVertexAdapter.RESPOSTA_INDISPONIVEL).doesNotContain("detalhe interno");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveResponderIndisponivelSemChamarApiQuandoNaoHaCredenciais() throws Exception {
        // Act
        String texto = adapter(null).chat("prompt", List.of(), "oi");

        // Assert
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThat(texto).isEqualTo(GeminiVertexAdapter.RESPOSTA_INDISPONIVEL);
    }

    private static String lerCorpo(HttpRequest requisicao) {
        HttpResponse.BodySubscriber<String> leitor = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        requisicao.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription assinatura) {
                leitor.onSubscribe(assinatura);
            }

            @Override
            public void onNext(ByteBuffer item) {
                leitor.onNext(List.of(item));
            }

            @Override
            public void onError(Throwable erro) {
                leitor.onError(erro);
            }

            @Override
            public void onComplete() {
                leitor.onComplete();
            }
        });
        return leitor.getBody().toCompletableFuture().join();
    }
}
