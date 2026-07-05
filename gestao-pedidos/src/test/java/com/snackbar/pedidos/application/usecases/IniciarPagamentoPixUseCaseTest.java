package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.pedidos.application.dto.IniciarPagamentoPixRequest;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort.CobrancaPixCriada;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.StatusPagamento;
import com.snackbar.pedidos.domain.valueobjects.DadosPix;

@ExtendWith(MockitoExtension.class)
class IniciarPagamentoPixUseCaseTest {

    @Mock
    private PagamentoRepositoryPort pagamentoRepository;

    @Mock
    private PedidoRepositoryPort pedidoRepository;

    @Mock
    private PixGatewayPort pixGateway;

    @InjectMocks
    private IniciarPagamentoPixUseCase useCase;

    private Pagamento pagamentoAguardandoPix(String correlationId) {
        Pagamento pagamento = Pagamento.iniciarParaPedido(
                "pedido-1", CanalPagamento.TOTEM, GatewayPagamento.SIMULADO,
                2500L, MeioPagamentoGateway.PIX, correlationId);
        pagamento.marcarAguardandoPix(new DadosPix(
                "txid-antigo", "payload", "b64", "copia", LocalDateTime.now().plusMinutes(3)));
        return pagamento;
    }

    @Test
    void deveRetornarCobrancaExistenteQuandoCorrelationIdRepetido() {
        var existente = pagamentoAguardandoPix("corr-1");
        when(pagamentoRepository.buscarPorCorrelationId("corr-1"))
                .thenReturn(Optional.of(existente));

        var resultado = useCase.executar(new IniciarPagamentoPixRequest("pedido-1", "corr-1"));

        assertEquals("txid-antigo", resultado.txid());
        verify(pixGateway, never()).criarCobrancaDinamica(any());
    }

    @Test
    void deveExpirarCobrancaPendenteAnteriorAoRegenerarQr() {
        var anterior = pagamentoAguardandoPix("corr-antiga");

        var pedidoMock = mock(Pedido.class);
        when(pedidoMock.getId()).thenReturn("pedido-1");
        when(pedidoMock.getValorTotal()).thenReturn(Preco.of(new BigDecimal("25.00")));

        when(pagamentoRepository.buscarPorCorrelationId("corr-nova")).thenReturn(Optional.empty());
        when(pedidoRepository.buscarPorId("pedido-1")).thenReturn(Optional.of(pedidoMock));
        when(pagamentoRepository.buscarAguardandoPixPorPedidoId("pedido-1")).thenReturn(Optional.of(anterior));
        when(pixGateway.gateway()).thenReturn(GatewayPagamento.SIMULADO);
        when(pixGateway.criarCobrancaDinamica(any())).thenReturn(new CobrancaPixCriada(
                "txid-novo", "payload", "b64", "copia", LocalDateTime.now().plusMinutes(3)));
        when(pagamentoRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = useCase.executar(new IniciarPagamentoPixRequest("pedido-1", "corr-nova"));

        assertEquals(StatusPagamento.EXPIRADO, anterior.getStatus());
        verify(pagamentoRepository).salvar(anterior);
        assertEquals("txid-novo", resultado.txid());
    }
}
