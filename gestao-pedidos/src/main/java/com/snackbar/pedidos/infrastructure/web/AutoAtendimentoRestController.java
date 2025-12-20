package com.snackbar.pedidos.infrastructure.web;

import com.snackbar.pedidos.application.dto.CriarPedidoAutoAtendimentoRequest;
import com.snackbar.pedidos.application.dto.PedidoAutoAtendimentoResponse;
import com.snackbar.pedidos.application.usecases.CriarPedidoAutoAtendimentoUseCase;
import com.snackbar.pedidos.application.usecases.BuscarPedidoPorIdUseCase;
import com.snackbar.pedidos.application.dto.PedidoDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para auto atendimento (totem).
 * 
 * IMPORTANTE: Este controller REQUER autenticação de operador.
 * O totem deve estar logado com um usuário operador para criar pedidos.
 * 
 * Diferente do PedidoMesaRestController (público), este:
 * - Requer autenticação JWT
 * - Cria pedidos diretamente (sem fila de pendentes)
 * - Não exige cliente cadastrado
 */
@RestController
@RequestMapping("/api/autoatendimento")
@RequiredArgsConstructor
@Slf4j
public class AutoAtendimentoRestController {

    private final CriarPedidoAutoAtendimentoUseCase criarPedidoUseCase;
    private final BuscarPedidoPorIdUseCase buscarPedidoUseCase;

    /**
     * Cria um pedido via auto atendimento (totem).
     * O pedido é criado diretamente no status PENDENTE.
     * 
     * @param request     Dados do pedido
     * @param userDetails Usuário autenticado (operador do totem)
     * @return PedidoAutoAtendimentoResponse com dados do pedido criado
     */
    @PostMapping("/pedido")
    public ResponseEntity<PedidoAutoAtendimentoResponse> criarPedido(
            @Valid @RequestBody CriarPedidoAutoAtendimentoRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("[AUTO-ATENDIMENTO] Criando pedido - Operador: {}, Cliente: {}",
                userDetails.getUsername(),
                request.getNomeCliente());

        String usuarioId = userDetails.getUsername();
        PedidoAutoAtendimentoResponse response = criarPedidoUseCase.executar(request, usuarioId);

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
