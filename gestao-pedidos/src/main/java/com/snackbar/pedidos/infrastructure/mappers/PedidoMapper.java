package com.snackbar.pedidos.infrastructure.mappers;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.pedidos.domain.entities.ItemPedido;
import com.snackbar.pedidos.domain.entities.MeioPagamentoPedido;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.valueobjects.NumeroPedido;
import com.snackbar.pedidos.infrastructure.persistence.ItemPedidoEntity;
import com.snackbar.pedidos.infrastructure.persistence.MeioPagamentoPedidoEntity;
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
                .sessaoId(pedido.getSessaoId())
                .mesaId(pedido.getMesaId())
                .nomeClienteMesa(pedido.getNomeClienteMesa())
                .dataPedido(pedido.getDataPedido())
                .dataFinalizacao(pedido.getDataFinalizacao())
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

        List<MeioPagamentoPedidoEntity> meiosPagamentoEntity = new ArrayList<>();
        for (MeioPagamentoPedido meioPagamentoPedido : pedido.getMeiosPagamento()) {
            MeioPagamentoPedidoEntity meioPagamentoEntity = MeioPagamentoPedidoEntity.builder()
                    .pedido(entity)
                    .meioPagamento(meioPagamentoPedido.getMeioPagamento())
                    .valor(meioPagamentoPedido.getValor().getAmount())
                    .build();
            meiosPagamentoEntity.add(meioPagamentoEntity);
        }
        entity.setMeiosPagamento(meiosPagamentoEntity);

        return entity;
    }

    public Pedido paraDomain(PedidoEntity entity) {
        NumeroPedido numeroPedido = NumeroPedido.of(entity.getNumeroPedido());

        Pedido pedido = Pedido.criar(
                numeroPedido,
                entity.getClienteId(),
                entity.getClienteNome(),
                entity.getUsuarioId());

        pedido.restaurarDoBanco(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());

        // Restaurar itens do banco SEM validações (não estamos adicionando, apenas
        // restaurando)
        List<ItemPedido> itensRestaurados = new ArrayList<>();
        for (ItemPedidoEntity itemEntity : entity.getItens()) {
            Preco precoUnitario = Preco.of(itemEntity.getPrecoUnitario());
            ItemPedido item = ItemPedido.criar(
                    itemEntity.getProdutoId(),
                    itemEntity.getProdutoNome(),
                    itemEntity.getQuantidade(),
                    precoUnitario,
                    itemEntity.getObservacoes());
            itensRestaurados.add(item);
        }
        pedido.restaurarItensDoBanco(itensRestaurados);

        // Restaurar meios de pagamento do banco
        List<MeioPagamentoPedido> meiosPagamentoRestaurados = new ArrayList<>();
        for (MeioPagamentoPedidoEntity meioPagamentoEntity : entity.getMeiosPagamento()) {
            Preco valor = Preco.of(meioPagamentoEntity.getValor());
            MeioPagamentoPedido meioPagamentoPedido = MeioPagamentoPedido.criar(
                    meioPagamentoEntity.getMeioPagamento(),
                    valor);
            meiosPagamentoRestaurados.add(meioPagamentoPedido);
        }
        pedido.restaurarMeiosPagamentoDoBanco(meiosPagamentoRestaurados);

        // Atualizar status e observações DEPOIS de restaurar os itens e meios de
        // pagamento
        pedido.atualizarStatus(entity.getStatus());
        pedido.atualizarObservacoes(entity.getObservacoes());
        pedido.definirSessaoId(entity.getSessaoId());

        // Restaurar dados da mesa (se houver)
        pedido.restaurarMesaDoBanco(entity.getMesaId(), entity.getNomeClienteMesa());

        // Restaurar data do pedido do banco (preserva a data original de criação)
        pedido.restaurarDataPedidoDoBanco(entity.getDataPedido());

        // Restaurar data de finalização do banco (preserva a data original de
        // finalização)
        pedido.restaurarDataFinalizacaoDoBanco(entity.getDataFinalizacao());

        return pedido;
    }
}
