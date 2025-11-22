package com.snackbar.pedidos.application.usecases;

import com.snackbar.pedidos.application.dto.AtualizarStatusPedidoRequest;
import com.snackbar.pedidos.application.dto.PedidoDTO;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.services.PedidoValidator;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AtualizarStatusPedidoUseCase {
    
    private final PedidoRepositoryPort pedidoRepository;
    private final PedidoValidator pedidoValidator;
    
    public PedidoDTO executar(String id, AtualizarStatusPedidoRequest request) {
        Pedido pedido = pedidoRepository.buscarPorId(id)
            .orElseThrow(() -> new ValidationException("Pedido não encontrado com ID: " + id));
        
        pedidoValidator.validarAtualizacaoStatus(pedido, request.getStatus());
        pedido.atualizarStatus(request.getStatus());
        
        Pedido pedidoAtualizado = pedidoRepository.salvar(pedido);
        
        return PedidoDTO.de(pedidoAtualizado);
    }
}

