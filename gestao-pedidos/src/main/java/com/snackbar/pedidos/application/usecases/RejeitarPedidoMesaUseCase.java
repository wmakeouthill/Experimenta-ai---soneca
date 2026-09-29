package com.snackbar.pedidos.application.usecases;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.PedidoPendenteDTO;
import com.snackbar.pedidos.application.services.FilaPedidosMesaService;
import com.snackbar.kernel.domain.exceptions.ConflitoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Use case para funcionário rejeitar um pedido pendente de mesa.
 * O pedido sai da fila sem criar pedido no sistema; o cliente vê o motivo no status.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RejeitarPedidoMesaUseCase {

    private final FilaPedidosMesaService filaPedidosMesa;

    public PedidoPendenteDTO executar(String pedidoPendenteId, String usuarioId, String motivo) {
        if (pedidoPendenteId == null || pedidoPendenteId.isBlank()) {
            throw new ValidationException("ID do pedido pendente é obrigatório");
        }

        PedidoPendenteDTO pedidoPendente = filaPedidosMesa.buscarVisivelPorId(pedidoPendenteId)
                .orElseThrow(RejeitarPedidoMesaUseCase::jaTratado);

        // Aceite em andamento segura a linha; depois dele o UPDATE não acha o pedido e vira 409
        if (!filaPedidosMesa.rejeitarPedido(pedidoPendenteId, motivo)) {
            throw jaTratado();
        }

        log.info("Pedido rejeitado - ID: {}, Mesa: {}, Usuário: {}, Motivo: {}",
                pedidoPendenteId,
                pedidoPendente.getNumeroMesa(),
                usuarioId,
                motivo != null ? motivo : "Não informado");

        return pedidoPendente;
    }

    private static ConflitoException jaTratado() {
        return new ConflitoException("Este pedido já foi aceito, rejeitado ou expirou.");
    }
}
