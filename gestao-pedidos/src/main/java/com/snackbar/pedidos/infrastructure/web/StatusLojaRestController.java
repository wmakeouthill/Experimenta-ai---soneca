package com.snackbar.pedidos.infrastructure.web;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.snackbar.pedidos.application.dto.SessaoTrabalhoDTO;
import com.snackbar.pedidos.application.dto.StatusLojaResponse;
import com.snackbar.pedidos.application.usecases.BuscarSessaoAtivaUseCase;
import com.snackbar.pedidos.domain.entities.StatusSessao;

import lombok.RequiredArgsConstructor;

/**
 * Controller REST público para consultar o status da loja (sessão de trabalho).
 * NÃO requer autenticação — usado por clientes via QR code e totem.
 *
 * Retorna se a loja está ABERTA, PAUSADA ou FECHADA com base na sessão de
 * trabalho ativa.
 */
@RestController
@RequestMapping("/api/public/status-loja")
@RequiredArgsConstructor
public class StatusLojaRestController {

    private final BuscarSessaoAtivaUseCase buscarSessaoAtivaUseCase;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();

    {
        // Heartbeat a cada 30s para manter conexões SSE vivas
        heartbeatExecutor.scheduleAtFixedRate(this::enviarHeartbeat, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Consulta o status atual da loja (request único).
     */
    @GetMapping
    public ResponseEntity<StatusLojaResponse> verificarStatus() {
        StatusLojaResponse response = construirStatusResponse();
        return ResponseEntity.ok(response);
    }

    /**
     * Stream SSE para receber atualizações de status em tempo real.
     * O frontend se conecta e recebe eventos quando o status muda.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L); // Sem timeout

        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // Envia o status atual imediatamente ao conectar
        try {
            StatusLojaResponse status = construirStatusResponse();
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(status));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Notifica todos os clientes conectados via SSE sobre mudança de status.
     * Chamado quando uma sessão é aberta, pausada, retomada ou finalizada.
     */
    public void notificarMudancaStatus() {
        StatusLojaResponse status = construirStatusResponse();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(status));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    private void enviarHeartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("ping")
                        .data(""));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    private StatusLojaResponse construirStatusResponse() {
        Optional<SessaoTrabalhoDTO> sessaoAtiva = buscarSessaoAtivaUseCase.executar();

        if (sessaoAtiva.isEmpty()) {
            return StatusLojaResponse.builder()
                    .status("FECHADA")
                    .mensagem("Estamos fechados no momento. Volte em breve!")
                    .numeroSessao(null)
                    .build();
        }

        SessaoTrabalhoDTO sessao = sessaoAtiva.get();

        if (sessao.getStatus() == StatusSessao.PAUSADA) {
            return StatusLojaResponse.builder()
                    .status("PAUSADA")
                    .mensagem("Estamos temporariamente indisponíveis. Voltamos em breve!")
                    .numeroSessao(sessao.getNumeroSessao())
                    .build();
        }

        return StatusLojaResponse.builder()
                .status("ABERTA")
                .mensagem(null)
                .numeroSessao(sessao.getNumeroSessao())
                .build();
    }
}
