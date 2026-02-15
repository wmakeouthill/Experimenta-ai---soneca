package com.snackbar.pedidos.infrastructure.web;

import com.snackbar.pedidos.infrastructure.idempotency.IdempotencyService;
import com.snackbar.kernel.security.JwtUserDetails;
import com.snackbar.pedidos.application.dto.CriarPedidoAutoAtendimentoRequest;
import com.snackbar.pedidos.application.dto.PedidoTotemNaFilaResponse;
import com.snackbar.pedidos.application.usecases.EnviarPedidoTotemParaFilaUseCase;
import com.snackbar.pedidos.application.usecases.BuscarPedidoPorIdUseCase;
import com.snackbar.pedidos.application.dto.PedidoDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para auto atendimento (totem).
 * 
 * IMPORTANTE: Este controller REQUER autenticação de operador.
 * O totem deve estar logado com um usuário operador para criar pedidos.
 * 
 * Diferente do PedidoMesaRestController (público), este:
 * - Requer autenticação JWT
 * - Envia o pedido para a fila de aceitação (igual ao fluxo de mesa)
 * - O pedido só vira pedido real quando um funcionário aceitar no painel
 * - Não exige cliente cadastrado
 *
 * Suporta idempotência via header X-Idempotency-Key para evitar
 * duplicação em caso de retry.
 */
@RestController
@RequestMapping("/api/autoatendimento")
@RequiredArgsConstructor
@Slf4j
public class AutoAtendimentoRestController {

    private final EnviarPedidoTotemParaFilaUseCase enviarPedidoTotemParaFilaUseCase;
    private final BuscarPedidoPorIdUseCase buscarPedidoUseCase;
    private final IdempotencyService idempotencyService;

    /**
     * Envia um pedido do totem para a fila de aceitação.
     * O pedido NÃO é criado como pedido real; fica pendente até um funcionário
     * aceitar no painel (igual ao fluxo de pedido de mesa).
     *
     * @param request        Dados do pedido
     * @param idempotencyKey Chave de idempotência opcional
     * @return PedidoTotemNaFilaResponse (id, status NA_FILA, mensagem)
     */
    @PostMapping("/pedido")
    public ResponseEntity<PedidoTotemNaFilaResponse> criarPedido(
            @Valid @RequestBody CriarPedidoAutoAtendimentoRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal())) {
            log.error("[AUTO-ATENDIMENTO] Tentativa de criar pedido sem autenticação");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof JwtUserDetails) {
            JwtUserDetails userDetails = (JwtUserDetails) principal;
            log.info("[AUTO-ATENDIMENTO] Enviando pedido para fila - Operador: {}, Cliente: {}",
                    userDetails.getEmail(),
                    request.getNomeCliente());
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyService.executeIdempotent(
                    idempotencyKey,
                    "POST /api/autoatendimento/pedido",
                    () -> enviarPedidoTotemParaFilaUseCase.executar(request),
                    PedidoTotemNaFilaResponse.class);
        }

        PedidoTotemNaFilaResponse response = enviarPedidoTotemParaFilaUseCase.executar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Busca o status de um pedido de auto atendimento.
     * 
     * @param pedidoId ID do pedido
     * @return PedidoDTO com o status atual
     */
    @GetMapping("/pedido/{pedidoId}/status")
    public ResponseEntity<PedidoDTO> buscarStatus(@PathVariable String pedidoId) {
        PedidoDTO pedido = buscarPedidoUseCase.executar(pedidoId);
        return ResponseEntity.ok(pedido);
    }
}
