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
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.ConfirmarPagamentoCartaoPresencialRequest;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.MeioPagamentoPedido;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.StatusPagamento;
import com.snackbar.pedidos.domain.valueobjects.DadosTef;

@ExtendWith(MockitoExtension.class)
class ConfirmarPagamentoCartaoPresencialUseCaseTest {

    @Mock private PagamentoRepositoryPort pagamentoRepository;
    @Mock private PedidoRepositoryPort pedidoRepository;

    @InjectMocks private ConfirmarPagamentoCartaoPresencialUseCase useCase;

    private Pagamento aguardandoTef() {
        Pagamento pagamento = Pagamento.iniciarParaPedido("ped-1", CanalPagamento.TOTEM,
                GatewayPagamento.SIMULADO, 2500, MeioPagamentoGateway.CARTAO_CREDITO, "corr-1");
        pagamento.marcarAguardandoTef();
        return pagamento;
    }

    private ConfirmarPagamentoCartaoPresencialRequest aprovado() {
        return new ConfirmarPagamentoCartaoPresencialRequest("corr-1", true, "37384", "MASTERCARD",
                "006299", "GETNET", "via do cliente", null);
    }

    private Pedido pedidoSemPagamento() {
        Pedido pedido = Mockito.mock(Pedido.class);
        when(pedido.getMeiosPagamento()).thenReturn(List.of());
        return pedido;
    }

    @Test
    void deveAprovarERegistrarOMeioNoPedido() {
        when(pagamentoRepository.buscarPorCorrelationId("corr-1"))
                .thenReturn(Optional.of(aguardandoTef()));
        Pedido pedido = pedidoSemPagamento();
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        when(pagamentoRepository.salvar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        var dto = useCase.executar(aprovado());

        assertEquals(StatusPagamento.APROVADO, dto.status());
        assertEquals("37384", dto.nsuTef());
        assertEquals("MASTERCARD", dto.bandeira());
        verify(pedido).adicionarMeioPagamento(any());
        verify(pedidoRepository).salvar(pedido);
    }

    /**
     * Reenvio da confirmacao (retry do totem apos falha de rede) nao pode cobrar de novo nem
     * registrar um segundo meio de pagamento no pedido.
     */
    @Test
    void deveSerIdempotenteQuandoOPagamentoJaEstaFinalizado() {
        Pagamento jaAprovado = aguardandoTef();
        jaAprovado.aprovarTef(new DadosTef("37384", "MASTERCARD", "006299", "GETNET", "via"));
        when(pagamentoRepository.buscarPorCorrelationId("corr-1"))
                .thenReturn(Optional.of(jaAprovado));

        var dto = useCase.executar(aprovado());

        assertEquals(StatusPagamento.APROVADO, dto.status());
        verify(pagamentoRepository, never()).salvar(any());
        verify(pedidoRepository, never()).buscarPorId(anyString());
    }

    @Test
    void deveNegarSemTocarNoPedido() {
        when(pagamentoRepository.buscarPorCorrelationId("corr-1"))
                .thenReturn(Optional.of(aguardandoTef()));
        when(pagamentoRepository.salvar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        var request = new ConfirmarPagamentoCartaoPresencialRequest("corr-1", false, null, null,
                null, null, null, "Cartao recusado pela adquirente");
        var dto = useCase.executar(request);

        assertEquals(StatusPagamento.NEGADO, dto.status());
        verify(pedidoRepository, never()).buscarPorId(anyString());
    }

    /**
     * Concorrencia: dois pagamentos com correlationIds diferentes tentando fechar o mesmo
     * pedido. O segundo tem que ser barrado antes de duplicar o meio de pagamento.
     */
    @Test
    void deveRecusarSegundoPagamentoNoMesmoPedido() {
        when(pagamentoRepository.buscarPorCorrelationId("corr-1"))
                .thenReturn(Optional.of(aguardandoTef()));
        Pedido pedido = Mockito.mock(Pedido.class);
        when(pedido.getMeiosPagamento()).thenReturn(List.of(
                MeioPagamentoPedido.criar(MeioPagamento.CARTAO_CREDITO,
                        Preco.of(new BigDecimal("25.00")))));
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));

        var erro = assertThrows(ValidationException.class, () -> useCase.executar(aprovado()));

        assertTrue(erro.getMessage().contains("ja possui pagamento"));
        verify(pedido, never()).adicionarMeioPagamento(any());
        verify(pedidoRepository, never()).salvar(any());
    }

    @Test
    void deveRejeitarCorrelationIdDesconhecido() {
        when(pagamentoRepository.buscarPorCorrelationId("corr-1")).thenReturn(Optional.empty());

        assertThrows(ValidationException.class, () -> useCase.executar(aprovado()));
    }
}
