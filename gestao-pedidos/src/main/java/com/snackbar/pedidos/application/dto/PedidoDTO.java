package com.snackbar.pedidos.application.dto;

import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.StatusPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoDTO {
    private String id;
    private String numeroPedido;
    private String clienteId;
    private String clienteNome;
    private StatusPedido status;
    private List<ItemPedidoDTO> itens;
    private BigDecimal valorTotal;
    private String observacoes;
    private String usuarioId;
    private LocalDateTime dataPedido;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static PedidoDTO de(Pedido pedido) {
        return PedidoDTO.builder()
            .id(pedido.getId())
            .numeroPedido(pedido.getNumeroPedido().getNumero())
            .clienteId(pedido.getClienteId())
            .clienteNome(pedido.getClienteNome())
            .status(pedido.getStatus())
            .itens(pedido.getItens().stream()
                .map(item -> ItemPedidoDTO.builder()
                    .produtoId(item.getProdutoId())
                    .produtoNome(item.getProdutoNome())
                    .quantidade(item.getQuantidade())
                    .precoUnitario(item.getPrecoUnitario().getAmount())
                    .subtotal(item.calcularSubtotal().getAmount())
                    .observacoes(item.getObservacoes())
                    .build())
                .collect(Collectors.toList()))
            .valorTotal(pedido.getValorTotal().getAmount())
            .observacoes(pedido.getObservacoes())
            .usuarioId(pedido.getUsuarioId())
            .dataPedido(pedido.getDataPedido())
            .createdAt(pedido.getCreatedAt())
            .updatedAt(pedido.getUpdatedAt())
            .build();
    }
}

