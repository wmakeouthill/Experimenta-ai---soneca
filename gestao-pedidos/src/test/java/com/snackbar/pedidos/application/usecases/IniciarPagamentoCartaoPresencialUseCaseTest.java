package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.IniciarPagamentoCartaoPresencialRequest;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.StatusPagamento;

@ExtendWith(MockitoExtension.class)
class IniciarPagamentoCartaoPresencialUseCaseTest {

    @Mock private PagamentoRepositoryPort pagamentoRepository;
    @Mock private PedidoRepositoryPort pedidoRepository;

    @InjectMocks private IniciarPagamentoCartaoPresencialUseCase useCase;

    private IniciarPagamentoCartaoPresencialRequest request(MeioPagamentoGateway meio) {
        return new IniciarPagamentoCartaoPresencialRequest("ped-1", meio, "corr-1");
    }

    private Pedido pedido(String valor) {
        Pedido pedido = Mockito.mock(Pedido.class);
        when(pedido.getId()).thenReturn("ped-1");
        when(pedido.getValorTotal()).thenReturn(Preco.of(new BigDecimal(valor)));
        return pedido;
    }

    @Test
    void deveIniciarAguardandoTefComOValorDoPedido() {
        Pedido pedido = pedido("25.00");
        when(pagamentoRepository.buscarPorCorrelationId("corr-1")).thenReturn(Optional.empty());
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        when(pagamentoRepository.salvarImediato(any()))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        var dto = useCase.executar(request(MeioPagamentoGateway.CARTAO_CREDITO));

        assertEquals(2500L, dto.valorCentavos());
        assertEquals(StatusPagamento.AGUARDANDO_TEF, dto.status());
        assertEquals(CanalPagamento.TOTEM, dto.canal());
        assertEquals("corr-1", dto.correlationId());
    }

    /** Duplo toque no totem: a segunda chamada devolve o mesmo pagamento, nao cria outro. */
    @Test
    void deveDevolverPagamentoExistenteQuandoCorrelationIdSeRepete() {
        Pagamento existente = Pagamento.iniciarParaPedido("ped-1", CanalPagamento.TOTEM,
                GatewayPagamento.SIMULADO, 2500, MeioPagamentoGateway.CARTAO_CREDITO, "corr-1");
        when(pagamentoRepository.buscarPorCorrelationId("corr-1"))
                .thenReturn(Optional.of(existente));

        var dto = useCase.executar(request(MeioPagamentoGateway.CARTAO_CREDITO));

        assertEquals("corr-1", dto.correlationId());
        verify(pedidoRepository, never()).buscarPorId(anyString());
        verify(pagamentoRepository, never()).salvarImediato(any());
    }

    /**
     * Duas requisicoes simultaneas passam juntas pela checagem acima; quem perde bate no
     * unique de correlation_id e precisa repetir — no retry o buscarPorCorrelationId acha.
     */
    @Test
    void deveTraduzirCorridaNoCorrelationIdEmOrientacaoDeRetry() {
        Pedido pedido = pedido("25.00");
        when(pagamentoRepository.buscarPorCorrelationId("corr-1")).thenReturn(Optional.empty());
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        when(pagamentoRepository.salvarImediato(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate correlation_id"));

        var pedidoRequest = request(MeioPagamentoGateway.CARTAO_CREDITO);
        var erro = assertThrows(ValidationException.class, () -> useCase.executar(pedidoRequest));

        assertTrue(erro.getMessage().contains("Tente novamente"));
    }

    @Test
    void deveRejeitarMeioQueNaoECartao() {
        var pixRequest = request(MeioPagamentoGateway.PIX);

        assertThrows(ValidationException.class, () -> useCase.executar(pixRequest));

        verify(pagamentoRepository, never()).salvarImediato(any());
    }

    @Test
    void deveRejeitarPedidoInexistente() {
        when(pagamentoRepository.buscarPorCorrelationId("corr-1")).thenReturn(Optional.empty());
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.empty());

        var pedidoRequest = request(MeioPagamentoGateway.CARTAO_CREDITO);

        assertThrows(ValidationException.class, () -> useCase.executar(pedidoRequest));

        verify(pagamentoRepository, never()).salvarImediato(any());
    }
}
