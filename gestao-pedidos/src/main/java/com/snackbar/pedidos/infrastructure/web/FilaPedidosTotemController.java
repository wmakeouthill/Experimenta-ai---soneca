package com.snackbar.pedidos.infrastructure.web;

import com.snackbar.pedidos.application.dto.PedidoDTO;
import com.snackbar.pedidos.application.dto.PedidoPendenteDTO;
import com.snackbar.pedidos.application.services.FilaPedidosTotemService;
import com.snackbar.pedidos.application.usecases.AceitarPedidoTotemUseCase;
import com.snackbar.pedidos.application.usecases.RejeitarPedidoTotemUseCase;
import com.snackbar.pedidos.infrastructure.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller REST para funcionários gerenciarem a fila de pedidos de totem (auto atendimento).
 *
 * REQUER AUTENTICAÇÃO - apenas funcionários logados podem:
 * - Ver pedidos pendentes do totem na fila
 * - Aceitar pedidos (cria pedido real e vai para "aguardando")
 * - Rejeitar pedidos (remove da fila)
 */
@RestController
@RequestMapping("/api/pedidos/fila-totem")
@RequiredArgsConstructor
public class FilaPedidosTotemController {

    private final FilaPedidosTotemService filaPedidosTotem;
    private final AceitarPedidoTotemUseCase aceitarPedidoTotemUseCase;
    private final RejeitarPedidoTotemUseCase rejeitarPedidoTotemUseCase;
    private final IdempotencyService idempotencyService;

    @GetMapping
    public ResponseEntity<List<PedidoPendenteDTO>> listarPedidosPendentes() {
        List<PedidoPendenteDTO> pedidos = filaPedidosTotem.listarPedidosPendentes();
        return ResponseEntity.ok(pedidos);
    }

    @GetMapping("/quantidade")
    public ResponseEntity<Map<String, Object>> quantidadePedidosPendentes() {
        int quantidade = filaPedidosTotem.quantidadePedidosPendentes();
        return ResponseEntity.ok(Map.of(
                "quantidade", quantidade,
                "existemPendentes", quantidade > 0));
    }

    @PostMapping("/{pedidoId}/aceitar")
    public ResponseEntity<PedidoDTO> aceitarPedido(
            @PathVariable String pedidoId,
            @RequestHeader("X-Usuario-Id") String usuarioId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyService.executeIdempotent(
                    idempotencyKey,
                    "POST /api/pedidos/fila-totem/" + pedidoId + "/aceitar",
                    () -> aceitarPedidoTotemUseCase.executar(pedidoId, usuarioId),
                    PedidoDTO.class);
        }

        PedidoDTO pedido = aceitarPedidoTotemUseCase.executar(pedidoId, usuarioId);
        return ResponseEntity.ok(pedido);
    }

    @PostMapping("/{pedidoId}/rejeitar")
    public ResponseEntity<Map<String, Object>> rejeitarPedido(
            @PathVariable String pedidoId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader("X-Usuario-Id") String usuarioId) {

        String motivo = body != null ? body.get("motivo") : null;

        PedidoPendenteDTO pedidoRejeitado = rejeitarPedidoTotemUseCase.executar(pedidoId, usuarioId, motivo);

        return ResponseEntity.ok(Map.of(
                "mensagem", "Pedido rejeitado com sucesso",
                "pedidoId", pedidoId,
                "cliente", pedidoRejeitado.getNomeCliente()));
    }

    @GetMapping("/{pedidoId}")
    public ResponseEntity<PedidoPendenteDTO> buscarPedidoPendente(@PathVariable String pedidoId) {
        return filaPedidosTotem.buscarPorId(pedidoId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
