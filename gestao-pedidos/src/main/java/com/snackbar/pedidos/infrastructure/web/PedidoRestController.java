package com.snackbar.pedidos.infrastructure.web;

import com.snackbar.pedidos.application.dto.AtualizarStatusPedidoRequest;
import com.snackbar.pedidos.application.dto.CriarPedidoRequest;
import com.snackbar.pedidos.application.dto.PedidoDTO;
import com.snackbar.pedidos.application.usecases.*;
import com.snackbar.pedidos.domain.entities.StatusPedido;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoRestController {
    
    private final CriarPedidoUseCase criarPedidoUseCase;
    private final ListarPedidosUseCase listarPedidosUseCase;
    private final BuscarPedidoPorIdUseCase buscarPedidoPorIdUseCase;
    private final AtualizarStatusPedidoUseCase atualizarStatusPedidoUseCase;
    private final CancelarPedidoUseCase cancelarPedidoUseCase;
    
    @PostMapping
    public ResponseEntity<PedidoDTO> criar(@Valid @RequestBody CriarPedidoRequest request) {
        PedidoDTO pedido = criarPedidoUseCase.executar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(pedido);
    }
    
    @GetMapping
    public ResponseEntity<List<PedidoDTO>> listar(
            @RequestParam(name = "status", required = false) StatusPedido status,
            @RequestParam(name = "clienteId", required = false) String clienteId,
            @RequestParam(name = "dataInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataInicio,
            @RequestParam(name = "dataFim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFim,
            @RequestParam(name = "sessaoId", required = false) String sessaoId) {
        List<PedidoDTO> pedidos;
        
        if (sessaoId != null) {
            pedidos = listarPedidosUseCase.executarPorSessaoId(sessaoId);
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
    public ResponseEntity<PedidoDTO> buscarPorId(@PathVariable String id) {
        PedidoDTO pedido = buscarPedidoPorIdUseCase.executar(id);
        return ResponseEntity.ok(pedido);
    }
    
    @PutMapping("/{id}/status")
    public ResponseEntity<PedidoDTO> atualizarStatus(
            @PathVariable String id,
            @Valid @RequestBody AtualizarStatusPedidoRequest request) {
        PedidoDTO pedido = atualizarStatusPedidoUseCase.executar(id, request);
        return ResponseEntity.ok(pedido);
    }
    
    @PutMapping("/{id}/cancelar")
    public ResponseEntity<PedidoDTO> cancelar(@PathVariable String id) {
        PedidoDTO pedido = cancelarPedidoUseCase.executar(id);
        return ResponseEntity.ok(pedido);
    }
}

