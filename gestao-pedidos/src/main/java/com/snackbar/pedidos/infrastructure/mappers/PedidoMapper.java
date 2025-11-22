package com.snackbar.pedidos.infrastructure.mappers;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.pedidos.domain.entities.ItemPedido;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.valueobjects.NumeroPedido;
import com.snackbar.pedidos.infrastructure.persistence.ItemPedidoEntity;
import com.snackbar.pedidos.infrastructure.persistence.PedidoEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PedidoMapper {
    
    public PedidoEntity paraEntity(Pedido pedido) {
        PedidoEntity entity = PedidoEntity.builder()
            .id(pedido.getId())
            .numeroPedido(pedido.getNumeroPedido().getNumero())
            .clienteId(pedido.getClienteId())
            .clienteNome(pedido.getClienteNome())
            .status(pedido.getStatus())
            .valorTotal(pedido.getValorTotal().getAmount())
            .observacoes(pedido.getObservacoes())
            .usuarioId(pedido.getUsuarioId())
            .dataPedido(pedido.getDataPedido())
            .createdAt(pedido.getCreatedAt())
            .updatedAt(pedido.getUpdatedAt())
            .build();
        
        List<ItemPedidoEntity> itensEntity = new ArrayList<>();
        for (ItemPedido item : pedido.getItens()) {
            ItemPedidoEntity itemEntity = ItemPedidoEntity.builder()
                .pedido(entity)
                .produtoId(item.getProdutoId())
                .produtoNome(item.getProdutoNome())
                .quantidade(item.getQuantidade())
                .precoUnitario(item.getPrecoUnitario().getAmount())
                .observacoes(item.getObservacoes())
                .build();
            itensEntity.add(itemEntity);
        }
        entity.setItens(itensEntity);
        
        return entity;
    }
    
    public Pedido paraDomain(PedidoEntity entity) {
        NumeroPedido numeroPedido = NumeroPedido.of(entity.getNumeroPedido());
        
        Pedido pedido = Pedido.criar(
            numeroPedido,
            entity.getClienteId(),
            entity.getClienteNome(),
            entity.getUsuarioId()
        );
        
        pedido.restaurarDoBanco(
            entity.getId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
        
        pedido.atualizarStatus(entity.getStatus());
        pedido.atualizarObservacoes(entity.getObservacoes());
        
        for (ItemPedidoEntity itemEntity : entity.getItens()) {
            Preco precoUnitario = Preco.of(itemEntity.getPrecoUnitario());
            ItemPedido item = ItemPedido.criar(
                itemEntity.getProdutoId(),
                itemEntity.getProdutoNome(),
                itemEntity.getQuantidade(),
                precoUnitario,
                itemEntity.getObservacoes()
            );
            pedido.adicionarItem(item);
        }
        
        return pedido;
    }
}

