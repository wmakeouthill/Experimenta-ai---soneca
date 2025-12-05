package com.snackbar.pedidos.application.usecases;

import com.snackbar.pedidos.application.dto.StatusPedidoClienteDTO;
import com.snackbar.pedidos.application.dto.StatusPedidoClienteDTO.StatusCliente;
import com.snackbar.pedidos.application.dto.PedidoPendenteDTO;
import com.snackbar.pedidos.application.services.FilaPedidosMesa;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.StatusPedido;
import com.snackbar.pedidos.domain.ports.PedidoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Use case para buscar o status de um pedido do ponto de vista do cliente.
 * Verifica tanto a fila de pendentes quanto os pedidos reais.
 */
@Service
@RequiredArgsConstructor
public class BuscarStatusPedidoClienteUseCase {

    private final FilaPedidosMesa filaPedidosMesa;
    private final PedidoRepositoryPort pedidoRepository;

    /**
     * Busca o status de um pedido pelo ID.
     * Primeiro verifica na fila de pendentes, depois nos pedidos reais.
     *
     * @param pedidoId ID do pedido (pode ser o ID do pedido pendente ou do pedido
     *                 real)
     * @return Status do pedido ou empty se não encontrado
     */
    public Optional<StatusPedidoClienteDTO> executar(String pedidoId) {
        // Primeiro tenta buscar na fila de pendentes
        Optional<PedidoPendenteDTO> pedidoPendente = filaPedidosMesa.buscarPorId(pedidoId);
        if (pedidoPendente.isPresent()) {
            return Optional.of(fromPedidoPendente(pedidoPendente.get()));
        }

        // Se não está na fila, busca nos pedidos reais
        Optional<Pedido> pedido = pedidoRepository.buscarPorId(pedidoId);
        if (pedido.isPresent()) {
            return Optional.of(fromPedido(pedido.get()));
        }

        return Optional.empty();
    }

    private StatusPedidoClienteDTO fromPedidoPendente(PedidoPendenteDTO pedidoPendente) {
        StatusCliente status = StatusCliente.AGUARDANDO_ACEITACAO;

        return StatusPedidoClienteDTO.builder()
                .pedidoId(pedidoPendente.getId())
                .status(status)
                .statusDescricao(status.getDescricao())
                .numeroMesa(pedidoPendente.getNumeroMesa())
                .dataHoraSolicitacao(pedidoPendente.getDataHoraSolicitacao())
                .tempoEsperaSegundos(pedidoPendente.getTempoEsperaSegundos())
                .build();
    }

    private StatusPedidoClienteDTO fromPedido(Pedido pedido) {
        StatusCliente status = mapStatusPedido(pedido.getStatus());

        long tempoEspera = 0;
        if (pedido.getDataPedido() != null) {
            tempoEspera = Duration.between(pedido.getDataPedido(), LocalDateTime.now()).getSeconds();
        }

        return StatusPedidoClienteDTO.builder()
                .pedidoId(pedido.getId())
                .status(status)
                .statusDescricao(status.getDescricao())
                .numeroMesa(pedido.getMesa() != null ? pedido.getMesa().getNumero() : null)
                .dataHoraSolicitacao(pedido.getDataPedido())
                .tempoEsperaSegundos(tempoEspera)
                .numeroPedido(pedido.getNumeroPedido())
                .build();
    }

    private StatusCliente mapStatusPedido(StatusPedido statusPedido) {
        return switch (statusPedido) {
            case PENDENTE -> StatusCliente.ACEITO;
            case PREPARANDO -> StatusCliente.PREPARANDO;
            case PRONTO -> StatusCliente.PRONTO;
            case FINALIZADO -> StatusCliente.FINALIZADO;
            case CANCELADO -> StatusCliente.CANCELADO;
        };
    }
}
