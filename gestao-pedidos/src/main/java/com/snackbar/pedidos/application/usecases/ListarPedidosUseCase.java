package com.snackbar.pedidos.application.usecases;

import com.snackbar.pedidos.application.dto.PedidoDTO;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.StatusPedido;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListarPedidosUseCase {
    
    private final PedidoRepositoryPort pedidoRepository;
    
    public List<PedidoDTO> executar() {
        return pedidoRepository.buscarTodos().stream()
            .map(PedidoDTO::de)
            .collect(Collectors.toList());
    }
    
    public List<PedidoDTO> executarPorStatus(StatusPedido status) {
        return pedidoRepository.buscarPorStatus(status).stream()
            .map(PedidoDTO::de)
            .collect(Collectors.toList());
    }
    
    public List<PedidoDTO> executarPorClienteId(String clienteId) {
        return pedidoRepository.buscarPorClienteId(clienteId).stream()
            .map(PedidoDTO::de)
            .collect(Collectors.toList());
    }
    
    public List<PedidoDTO> executarPorDataPedido(LocalDateTime dataInicio, LocalDateTime dataFim) {
        return pedidoRepository.buscarPorDataPedido(dataInicio, dataFim).stream()
            .map(PedidoDTO::de)
            .collect(Collectors.toList());
    }
    
    public List<PedidoDTO> executarPorStatusEData(StatusPedido status, LocalDateTime dataInicio, LocalDateTime dataFim) {
        return pedidoRepository.buscarPorStatusEData(status, dataInicio, dataFim).stream()
            .map(PedidoDTO::de)
            .collect(Collectors.toList());
    }
}

