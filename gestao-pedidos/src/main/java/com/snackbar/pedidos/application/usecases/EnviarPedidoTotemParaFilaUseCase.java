package com.snackbar.pedidos.application.usecases;

import com.snackbar.pedidos.application.dto.CriarPedidoAutoAtendimentoRequest;
import com.snackbar.pedidos.application.dto.PedidoPendenteDTO;
import com.snackbar.pedidos.application.dto.PedidoTotemNaFilaResponse;
import com.snackbar.pedidos.application.services.FilaPedidosTotemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case para enviar um pedido do totem para a fila de aceitação.
 * O pedido não é criado como pedido real; fica pendente até um funcionário aceitar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnviarPedidoTotemParaFilaUseCase {

    private final FilaPedidosTotemService filaPedidosTotem;

    @Transactional
    public PedidoTotemNaFilaResponse executar(CriarPedidoAutoAtendimentoRequest request) {
        PedidoPendenteDTO pendente = filaPedidosTotem.adicionarPedido(request);

        return PedidoTotemNaFilaResponse.builder()
                .id(pendente.getId())
                .status("NA_FILA")
                .mensagem("Pedido enviado! Aguarde a confirmação do atendente.")
                .build();
    }
}
