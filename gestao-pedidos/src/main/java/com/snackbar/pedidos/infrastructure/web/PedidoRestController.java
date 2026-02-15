package com.snackbar.pedidos.infrastructure.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import com.snackbar.pedidos.application.dto.*;
import com.snackbar.pedidos.application.usecases.*;
import com.snackbar.pedidos.domain.entities.StatusPedido;
import com.snackbar.pedidos.infrastructure.idempotency.IdempotencyService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoRestController {

    private final CriarPedidoUseCase criarPedidoUseCase;
    private final ListarPedidosUseCase listarPedidosUseCase;
    private final BuscarPedidoPorIdUseCase buscarPedidoPorIdUseCase;
    private final AtualizarStatusPedidoUseCase atualizarStatusPedidoUseCase;
    private final CancelarPedidoUseCase cancelarPedidoUseCase;
    private final ExcluirPedidoUseCase excluirPedidoUseCase;
    private final RegistrarPagamentoPedidoUseCase registrarPagamentoPedidoUseCase;
    private final CorrigirTrocoPedidoUseCase corrigirTrocoPedidoUseCase;
    private final IdempotencyService idempotencyService;

    /**
     * Cria um novo pedido.
     * 
     * Suporta idempotência via header X-Idempotency-Key para evitar
     * criação de pedidos duplicados em caso de retry.
     */
    @PostMapping
    public ResponseEntity<PedidoDTO> criar(
            @Valid @RequestBody CriarPedidoRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyService.executeIdempotent(
                    idempotencyKey,
                    "POST /api/pedidos",
                    () -> criarPedidoUseCase.executar(request),
                    PedidoDTO.class);
        }

        PedidoDTO pedido = criarPedidoUseCase.executar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(pedido);
    }

    @GetMapping
    public ResponseEntity<List<PedidoDTO>> listar(
            @RequestParam(name = "status", required = false) StatusPedido status,
            @RequestParam(name = "clienteId", required = false) String clienteId,
            @RequestParam(name = "dataInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataInicio,
            @RequestParam(name = "dataFim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFim,
            @RequestParam(name = "sessaoId", required = false) String sessaoId,
            @RequestParam(name = "dataInicioSessao", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicioSessao) {
        List<PedidoDTO> pedidos;

        if (sessaoId != null) {
            pedidos = listarPedidosUseCase.executarPorSessaoId(sessaoId);
        } else if (dataInicioSessao != null) {
            pedidos = listarPedidosUseCase.executarPorDataInicioSessao(dataInicioSessao);
        } else if (status != null && dataInicio != null && dataFim != null) {
            pedidos = listarPedidosUseCase.executarPorStatusEData(status, dataInicio, dataFim);
        } else if (status != null) {
            pedidos = listarPedidosUseCase.executarPorStatus(status);
        } else if (clienteId != null) {
            pedidos = listarPedidosUseCase.executarPorClienteId(clienteId);
        } else if (dataInicio != null && dataFim != null) {
            pedidos = listarPedidosUseCase.executarPorDataPedido(dataInicio, dataFim);
        } else {
            pedidos = listarPedidosUseCase.executar();
        }

        return ResponseEntity.ok(pedidos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoDTO> buscarPorId(@NonNull @PathVariable String id) {
        PedidoDTO pedido = buscarPedidoPorIdUseCase.executar(id);
        return ResponseEntity.ok(pedido);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<PedidoDTO> atualizarStatus(
            @NonNull @PathVariable String id,
            @Valid @RequestBody AtualizarStatusPedidoRequest request) {
        PedidoDTO pedido = atualizarStatusPedidoUseCase.executar(id, request);
        return ResponseEntity.ok(pedido);
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<PedidoDTO> cancelar(@NonNull @PathVariable String id) {
        PedidoDTO pedido = cancelarPedidoUseCase.executar(id);
        return ResponseEntity.ok(pedido);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@NonNull @PathVariable String id) {
        excluirPedidoUseCase.executar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Registra pagamento para um pedido existente.
     * Utilizado para pedidos de mesa que não têm pagamento definido na criação.
     */
    @PostMapping("/{id}/pagamento")
    public ResponseEntity<PedidoDTO> registrarPagamento(
            @NonNull @PathVariable String id,
            @Valid @RequestBody RegistrarPagamentoPedidoRequest request) {
        PedidoDTO pedido = registrarPagamentoPedidoUseCase.executar(id, request.getMeiosPagamento());
        return ResponseEntity.ok(pedido);
    }

    /**
     * Corrige o troco de um pedido com pagamento em dinheiro.
     * Utilizado pelo operador quando o cliente não informou o valor pago
     * no momento do pedido e precisa ser corrigido no ato do pagamento.
     */
    @PatchMapping("/{id}/troco")
    public ResponseEntity<PedidoDTO> corrigirTroco(
            @NonNull @PathVariable String id,
            @Valid @RequestBody CorrigirTrocoRequest request) {
        PedidoDTO pedido = corrigirTrocoPedidoUseCase.executar(id, request.getValorPagoDinheiro());
        return ResponseEntity.ok(pedido);
    }
}
