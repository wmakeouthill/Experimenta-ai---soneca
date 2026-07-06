package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import com.snackbar.pedidos.application.ports.ContaMesaRepositoryPort;
import com.snackbar.pedidos.application.ports.MesaRepositoryPort;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort.CobrancaPixCriada;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.Mesa;
import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;
import com.snackbar.pedidos.domain.entities.Pedido;

@ExtendWith(MockitoExtension.class)
class FecharContaMesaUseCaseTest {

    @Mock private PedidoRepositoryPort pedidoRepository;
    @Mock private ContaMesaRepositoryPort contaRepository;
    @Mock private PixGatewayPort pixGateway;
    @Mock private PagamentoRepositoryPort pagamentoRepository;
    @Mock private MesaRepositoryPort mesaRepository;
    @Mock private ConfiguracaoPagamentoRepositoryPort configuracaoRepository;

    @InjectMocks private FecharContaMesaUseCase useCase;

    private Mesa mesa() {
        Mesa mesa = org.mockito.Mockito.mock(Mesa.class);
        when(mesa.getId()).thenReturn("mesa-1");
        lenient().when(mesa.getNumero()).thenReturn(7);
        return mesa;
    }

    private ConfiguracaoPagamentoDTO configPosPago() {
        return new ConfiguracaoPagamentoDTO(false, false, true, false, ModoPagamentoMesa.POS_PAGO);
    }

    private Pedido pedido(String id, String valor) {
        Pedido pedido = org.mockito.Mockito.mock(Pedido.class);
        when(pedido.getId()).thenReturn(id);
        when(pedido.getValorTotal()).thenReturn(
                com.snackbar.cardapio.domain.valueobjects.Preco.of(new BigDecimal(valor)));
        return pedido;
    }

    @Test
    void deveConsolidarPedidosEIniciarCobrancaPix() {
        Mesa mesa = mesa();
        Pedido pedido1 = pedido("p1", "30.00");
        Pedido pedido2 = pedido("p2", "20.00");

        when(mesaRepository.buscarPorQrCodeToken("token-1")).thenReturn(Optional.of(mesa));
        when(configuracaoRepository.buscar()).thenReturn(configPosPago());
        when(contaRepository.buscarAbertaPorMesaECliente("mesa-1", "cliente-1"))
                .thenReturn(Optional.empty());
        when(pedidoRepository.buscarAbertosPorMesaESemPagamento("mesa-1", "cliente-1"))
                .thenReturn(List.of(pedido1, pedido2));
        when(contaRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pixGateway.gateway()).thenReturn(GatewayPagamento.SIMULADO);
        when(pixGateway.criarCobrancaDinamica(any())).thenReturn(new CobrancaPixCriada(
                "txid-1", "payload", "b64", "copia", LocalDateTime.now().plusMinutes(3)));
        when(pagamentoRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = useCase.executar("corr-1", "token-1", "cliente-1");

        assertEquals(5000L, resultado.conta().valorCentavos());
        assertEquals(2, resultado.conta().pedidos().size());
        assertEquals("copia", resultado.pagamento().copiaECola());
    }

    @Test
    void deveRejeitarQuandoNaoHaPedidosAbertos() {
        Mesa mesa = mesa();

        when(mesaRepository.buscarPorQrCodeToken("token-1")).thenReturn(Optional.of(mesa));
        when(configuracaoRepository.buscar()).thenReturn(configPosPago());
        when(contaRepository.buscarAbertaPorMesaECliente("mesa-1", "cliente-1"))
                .thenReturn(Optional.empty());
        when(pedidoRepository.buscarAbertosPorMesaESemPagamento("mesa-1", "cliente-1"))
                .thenReturn(List.of());

        assertThrows(ValidationException.class,
                () -> useCase.executar("corr-1", "token-1", "cliente-1"));
    }

    @Test
    void deveRejeitarQuandoModoNaoEPosPago() {
        when(configuracaoRepository.buscar()).thenReturn(
                new ConfiguracaoPagamentoDTO(false, false, true, false, ModoPagamentoMesa.PRE_PAGO));
        assertThrows(ValidationException.class,
                () -> useCase.executar("corr-1", "token-1", "cliente-1"));
    }

    @Test
    void deveTraduzirViolacaoDeConcorrenciaEmValidationException() {
        Mesa mesa = mesa();
        when(mesaRepository.buscarPorQrCodeToken("token-1")).thenReturn(Optional.of(mesa));
        when(configuracaoRepository.buscar()).thenReturn(configPosPago());
        when(contaRepository.buscarAbertaPorMesaECliente("mesa-1", "cliente-1"))
                .thenReturn(Optional.empty());
        Pedido pedido1 = pedido("p1", "30.00");
        when(pedidoRepository.buscarAbertosPorMesaESemPagamento("mesa-1", "cliente-1"))
                .thenReturn(List.of(pedido1));
        when(contaRepository.salvar(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
        assertThrows(ValidationException.class,
                () -> useCase.executar("corr-1", "token-1", "cliente-1"));
    }
}
