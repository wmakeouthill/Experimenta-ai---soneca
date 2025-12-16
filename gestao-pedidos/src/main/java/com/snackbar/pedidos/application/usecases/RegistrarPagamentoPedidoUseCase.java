package com.snackbar.pedidos.application.usecases;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.MeioPagamentoRequest;
import com.snackbar.pedidos.application.dto.PedidoDTO;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.MeioPagamentoPedido;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.StatusPedido;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Use case para registrar pagamento de um pedido existente.
 * 
 * Utilizado principalmente para pedidos de mesa que não têm pagamento
 * definido no momento da criação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrarPagamentoPedidoUseCase {

    private final PedidoRepositoryPort pedidoRepository;

    @Transactional
    public PedidoDTO executar(@NonNull String pedidoId, @NonNull List<MeioPagamentoRequest> meiosPagamento) {
        Pedido pedido = pedidoRepository.buscarPorId(pedidoId)
                .orElseThrow(() -> new ValidationException("Pedido não encontrado com ID: " + pedidoId));

        // Valida que o pedido não está cancelado
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new ValidationException("Não é possível registrar pagamento para pedido cancelado");
        }

        // Valida que não há pagamento já registrado
        if (pedido.getMeiosPagamento() != null && !pedido.getMeiosPagamento().isEmpty()) {
            throw new ValidationException("Pedido já possui pagamento registrado");
        }

        // Adiciona os meios de pagamento
        BigDecimal totalPagamento = BigDecimal.ZERO;
        for (MeioPagamentoRequest meioPagamentoRequest : meiosPagamento) {
            Preco valor = Preco.of(meioPagamentoRequest.getValor());
            MeioPagamentoPedido meioPagamentoPedido = MeioPagamentoPedido.criar(
                    meioPagamentoRequest.getMeioPagamento(),
                    valor);
            pedido.adicionarMeioPagamento(meioPagamentoPedido);
            totalPagamento = totalPagamento.add(meioPagamentoRequest.getValor());
        }

        // Valida que o total pago é igual ao valor do pedido
        BigDecimal valorPedido = pedido.getValorTotal().getAmount();
        if (totalPagamento.compareTo(valorPedido) != 0) {
            throw new ValidationException(
                    String.format("Valor do pagamento (R$ %.2f) deve ser igual ao valor do pedido (R$ %.2f)",
                            totalPagamento.doubleValue(),
                            valorPedido.doubleValue()));
        }

        Pedido pedidoAtualizado = pedidoRepository.salvar(pedido);

        log.info("[PAGAMENTO] Pagamento registrado para pedido {}: R$ {}",
                pedidoAtualizado.getNumeroPedido().getNumero(),
                totalPagamento);

        return PedidoDTO.de(pedidoAtualizado);
    }
}
