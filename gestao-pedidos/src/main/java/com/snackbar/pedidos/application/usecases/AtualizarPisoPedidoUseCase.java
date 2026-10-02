package com.snackbar.pedidos.application.usecases;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.kernel.domain.exceptions.RecursoNaoEncontradoException;
import com.snackbar.pedidos.application.dto.PedidoDTO;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.Piso;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Move um pedido já criado para o painel do térreo ou do 1º andar.
 * Cada TV do lobby só lista o piso dela.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtualizarPisoPedidoUseCase {

    private final PedidoRepositoryPort pedidoRepository;

    @Transactional
    public PedidoDTO executar(@NonNull String pedidoId, @NonNull Piso piso) {
        Pedido pedido = pedidoRepository.buscarPorId(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado com ID: " + pedidoId));

        if (pedido.getPiso() == piso) {
            return PedidoDTO.de(pedido);
        }

        pedido.definirPiso(piso);
        Pedido pedidoAtualizado = pedidoRepository.salvar(pedido);

        log.info("[PISO] Pedido {} movido para o painel {}",
                pedidoAtualizado.getNumeroPedido().getNumero(), piso);

        return PedidoDTO.de(pedidoAtualizado);
    }
}
