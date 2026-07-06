package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;
import com.snackbar.pedidos.application.ports.ConfiguracaoPagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.MesaRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.Mesa;
import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;

@ExtendWith(MockitoExtension.class)
class ConsultarContaMesaUseCaseTest {

    @Mock private PedidoRepositoryPort pedidoRepository;
    @Mock private MesaRepositoryPort mesaRepository;
    @Mock private ConfiguracaoPagamentoRepositoryPort configuracaoRepository;

    @InjectMocks private ConsultarContaMesaUseCase useCase;

    private Mesa mesa() {
        Mesa mesa = org.mockito.Mockito.mock(Mesa.class);
        when(mesa.getId()).thenReturn("mesa-1");
        lenient().when(mesa.getNumero()).thenReturn(7);
        return mesa;
    }

    private ConfiguracaoPagamentoDTO configPosPago() {
        return new ConfiguracaoPagamentoDTO(false, false, true, false, ModoPagamentoMesa.POS_PAGO);
    }

    @Test
    void deveRejeitarQuandoModoNaoEPosPago() {
        when(configuracaoRepository.buscar()).thenReturn(
                new ConfiguracaoPagamentoDTO(false, false, true, false, ModoPagamentoMesa.PRE_PAGO));

        assertThrows(ValidationException.class,
                () -> useCase.executar("token-1", "cliente-1"));
    }

    @Test
    void deveRetornarContaVaziaQuandoNaoHaPedidosAbertos() {
        Mesa mesa = mesa();
        when(configuracaoRepository.buscar()).thenReturn(configPosPago());
        when(mesaRepository.buscarPorQrCodeToken("token-1")).thenReturn(Optional.of(mesa));
        when(pedidoRepository.buscarAbertosPorMesaESemPagamento("mesa-1", "cliente-1"))
                .thenReturn(List.of());

        var resultado = useCase.executar("token-1", "cliente-1");

        assertEquals(0L, resultado.valorCentavos());
    }
}
