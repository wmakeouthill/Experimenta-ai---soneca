package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.kernel.domain.exceptions.RecursoNaoEncontradoException;
import com.snackbar.pedidos.application.dto.PedidoDTO;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.Piso;
import com.snackbar.pedidos.domain.valueobjects.NumeroPedido;

@ExtendWith(MockitoExtension.class)
class AtualizarPisoPedidoUseCaseTest {

    @Mock private PedidoRepositoryPort pedidoRepository;

    @InjectMocks private AtualizarPisoPedidoUseCase useCase;

    private Pedido pedidoNoPiso(Piso piso) {
        Pedido pedido = Pedido.criar(NumeroPedido.de(12), "cli-1", "Balcão", "usr-1");
        pedido.definirPiso(piso);
        return pedido;
    }

    @Test
    void deveMoverOPedidoParaOOutroPainel() {
        Pedido pedido = pedidoNoPiso(Piso.TERREO);
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        when(pedidoRepository.salvar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        PedidoDTO atualizado = useCase.executar("ped-1", Piso.ANDAR);

        assertEquals(Piso.ANDAR, pedido.getPiso());
        assertEquals(Piso.ANDAR, atualizado.getPiso());
        verify(pedidoRepository).salvar(pedido);
    }

    @Test
    void naoGravaDeNovoQuandoOPedidoJaEstaNoPainelPedido() {
        Pedido pedido = pedidoNoPiso(Piso.TERREO);
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));

        PedidoDTO atual = useCase.executar("ped-1", Piso.TERREO);

        assertEquals(Piso.TERREO, atual.getPiso());
        verify(pedidoRepository, never()).salvar(any());
    }

    @Test
    void rejeitaPedidoInexistente() {
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> useCase.executar("ped-1", Piso.ANDAR));
        verify(pedidoRepository, never()).salvar(any());
    }
}
