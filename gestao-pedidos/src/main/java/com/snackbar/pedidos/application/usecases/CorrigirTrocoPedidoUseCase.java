package com.snackbar.pedidos.application.usecases;

import java.math.BigDecimal;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.PedidoDTO;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.Pedido;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case para corrigir o troco de um pedido com pagamento em dinheiro.
 *
 * Utilizado pelo operador quando o cliente não informou o valor pago
 * em dinheiro no momento do pedido (CTA) e precisa ser corrigido
 * posteriormente no ato do pagamento.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CorrigirTrocoPedidoUseCase {

    private final PedidoRepositoryPort pedidoRepository;

    /**
     * Corrige o valor pago em dinheiro e recalcula o troco.
     *
     * @param pedidoId          ID do pedido
     * @param valorPagoDinheiro valor real que o cliente entregou em nota
     * @return o pedido atualizado
     */
    @Transactional
    public PedidoDTO executar(@NonNull String pedidoId, @NonNull BigDecimal valorPagoDinheiro) {
        Pedido pedido = pedidoRepository.buscarPorId(pedidoId)
                .orElseThrow(() -> new ValidationException("Pedido não encontrado com ID: " + pedidoId));

        Preco novoValorPago = Preco.of(valorPagoDinheiro);
        pedido.corrigirTrocoDinheiro(novoValorPago);

        Pedido pedidoAtualizado = pedidoRepository.salvar(pedido);

        log.info("[TROCO] Troco corrigido para pedido {}: valor pago R$ {}, troco R$ {}",
                pedidoAtualizado.getNumeroPedido().getNumero(),
                valorPagoDinheiro,
                novoValorPago.subtract(pedido.getValorTotal()).getAmount());

        return PedidoDTO.de(pedidoAtualizado);
    }
}
