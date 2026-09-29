package com.snackbar.pedidos.application.usecases;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.PedidoPendenteDTO;
import com.snackbar.pedidos.application.services.FilaPedidosTotemService;
import com.snackbar.kernel.domain.exceptions.ConflitoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Use case para funcionário rejeitar um pedido pendente de totem.
 * O pedido é removido da fila sem criar pedido no sistema.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RejeitarPedidoTotemUseCase {

    private final FilaPedidosTotemService filaPedidosTotem;

    public PedidoPendenteDTO executar(String pedidoPendenteId, String usuarioId, String motivo) {
        if (pedidoPendenteId == null || pedidoPendenteId.isBlank()) {
            throw new ValidationException("ID do pedido pendente é obrigatório");
        }

        PedidoPendenteDTO pedidoPendente = filaPedidosTotem.buscarPorId(pedidoPendenteId)
                .orElseThrow(RejeitarPedidoTotemUseCase::jaTratado);

        // Aceite em andamento segura a linha; depois dele o UPDATE não acha o pedido e vira 409
        if (!filaPedidosTotem.rejeitarPedido(pedidoPendenteId, motivo)) {
            throw jaTratado();
        }

        log.info("Pedido totem rejeitado - ID: {}, Cliente: {}, Usuário: {}, Motivo: {}",
                pedidoPendenteId,
                pedidoPendente.getNomeCliente(),
                usuarioId,
                motivo != null ? motivo : "Não informado");

        return pedidoPendente;
    }

    private static ConflitoException jaTratado() {
        return new ConflitoException("Este pedido já foi aceito, rejeitado ou expirou.");
    }
}
