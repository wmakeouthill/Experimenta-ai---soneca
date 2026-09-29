package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.kernel.domain.exceptions.ConflitoException;
import com.snackbar.pedidos.application.dto.PedidoPendenteDTO;
import com.snackbar.pedidos.application.dto.StatusPedidoClienteDTO;
import com.snackbar.pedidos.application.dto.StatusPedidoClienteDTO.StatusCliente;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.services.FilaPedidosMesaService;

@ExtendWith(MockitoExtension.class)
class RejeitarPedidoMesaUseCaseTest {

    @Mock
    private FilaPedidosMesaService filaPedidosMesa;
    @Mock
    private PedidoRepositoryPort pedidoRepository;

    @InjectMocks
    private RejeitarPedidoMesaUseCase useCase;

    @Test
    void aceiteQueVenceuARejeicaoViraConflito() {
        when(filaPedidosMesa.buscarVisivelPorId("p1")).thenReturn(Optional.of(pendente(null)));
        when(filaPedidosMesa.rejeitarPedido("p1", "Acabou o pão")).thenReturn(false);

        assertThrows(ConflitoException.class, () -> useCase.executar("p1", "u1", "Acabou o pão"));
    }

    @Test
    void clienteVeOMotivoDaRejeicaoNoStatus() {
        BuscarStatusPedidoClienteUseCase status = new BuscarStatusPedidoClienteUseCase(filaPedidosMesa, pedidoRepository);
        when(filaPedidosMesa.buscarPorId("p1")).thenReturn(Optional.empty());
        when(pedidoRepository.buscarPorId("p1")).thenReturn(Optional.empty());
        when(filaPedidosMesa.buscarPedidoRealPorPendente("p1")).thenReturn(Optional.empty());
        when(filaPedidosMesa.buscarRejeitadoPorId("p1")).thenReturn(Optional.of(pendente("Acabou o pão")));

        StatusPedidoClienteDTO dto = status.executar("p1").orElseThrow();

        assertEquals(StatusCliente.CANCELADO, dto.getStatus());
        assertEquals("Acabou o pão", dto.getMotivoCancelamento());
    }

    private static PedidoPendenteDTO pendente(String motivoRejeicao) {
        return PedidoPendenteDTO.builder().id("p1").numeroMesa(3).motivoRejeicao(motivoRejeicao).build();
    }
}
