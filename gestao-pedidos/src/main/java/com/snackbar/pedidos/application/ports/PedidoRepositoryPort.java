package com.snackbar.pedidos.application.ports;

import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.StatusPedido;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PedidoRepositoryPort {
    Pedido salvar(Pedido pedido);
    Optional<Pedido> buscarPorId(String id);
    List<Pedido> buscarTodos();
    List<Pedido> buscarPorStatus(StatusPedido status);
    List<Pedido> buscarPorClienteId(String clienteId);
    List<Pedido> buscarPorDataPedido(LocalDateTime dataInicio, LocalDateTime dataFim);
    List<Pedido> buscarPorStatusEData(StatusPedido status, LocalDateTime dataInicio, LocalDateTime dataFim);
    List<Pedido> buscarPorSessaoId(String sessaoId);
    int buscarUltimoNumeroPedido();
}

