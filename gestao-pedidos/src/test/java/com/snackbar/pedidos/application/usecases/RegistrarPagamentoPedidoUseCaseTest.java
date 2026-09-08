package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.MeioPagamentoRequest;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.services.AuditoriaPagamentoService;
import com.snackbar.pedidos.domain.entities.ItemPedido;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoPedido;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.StatusPedido;
import com.snackbar.pedidos.domain.valueobjects.NumeroPedido;

@ExtendWith(MockitoExtension.class)
class RegistrarPagamentoPedidoUseCaseTest {

    @Mock private PedidoRepositoryPort pedidoRepository;
    @Mock private AuditoriaPagamentoService auditoriaPagamentoService;

    @InjectMocks private RegistrarPagamentoPedidoUseCase useCase;

    /** Pedido real: o guard de valor compara com o total recalculado pelo dominio. */
    private Pedido pedidoDe(String valor) {
        Pedido pedido = Pedido.criarPedidoAutoAtendimento(NumeroPedido.de(1), "Cliente", "usr-1");
        pedido.adicionarItem(ItemPedido.criar("prod-1", "X-Burger", 1,
                Preco.of(new BigDecimal(valor)), null));
        return pedido;
    }

    private MeioPagamentoRequest dinheiro(String valor, String valorPago) {
        return new MeioPagamentoRequest(MeioPagamento.DINHEIRO, new BigDecimal(valor),
                valorPago == null ? null : new BigDecimal(valorPago), null);
    }

    private MeioPagamentoRequest cartao(String valor) {
        return new MeioPagamentoRequest(MeioPagamento.CARTAO_CREDITO, new BigDecimal(valor), null, null);
    }

    private void repositorioDevolveOProprioPedido() {
        when(pedidoRepository.salvar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    @Test
    void deveRegistrarPagamentoQueFechaComOValorDoPedido() {
        Pedido pedido = pedidoDe("25.00");
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        repositorioDevolveOProprioPedido();

        var resultado = useCase.executar("ped-1", List.of(cartao("25.00")));

        assertEquals(new BigDecimal("25.00"), resultado.getValorTotal());
        assertEquals(1, pedido.getMeiosPagamento().size());
    }

    /** Aceitar um total diferente do pedido e caixa furado: o cliente sai pagando menos. */
    @Test
    void deveRejeitarPagamentoAbaixoDoValorDoPedido() {
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedidoDe("25.00")));
        var meios = List.of(cartao("20.00"));

        assertThrows(ValidationException.class, () -> useCase.executar("ped-1", meios));

        verify(pedidoRepository, never()).salvar(any());
    }

    @Test
    void deveRejeitarPagamentoAcimaDoValorDoPedido() {
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedidoDe("25.00")));
        var meios = List.of(cartao("30.00"));

        assertThrows(ValidationException.class, () -> useCase.executar("ped-1", meios));

        verify(pedidoRepository, never()).salvar(any());
    }

    @Test
    void devePermitirPagamentoDivididoQueSomaOValorExato() {
        Pedido pedido = pedidoDe("25.00");
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        repositorioDevolveOProprioPedido();

        useCase.executar("ped-1", List.of(cartao("10.00"), dinheiro("15.00", "20.00")));

        assertEquals(2, pedido.getMeiosPagamento().size());
    }

    @Test
    void deveCalcularTrocoQuandoOClienteInformaOValorEmNota() {
        Pedido pedido = pedidoDe("25.00");
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        repositorioDevolveOProprioPedido();

        useCase.executar("ped-1", List.of(dinheiro("25.00", "50.00")));

        MeioPagamentoPedido meio = pedido.getMeiosPagamento().get(0);
        assertEquals(new BigDecimal("25.00"), meio.getTroco().getAmount());
    }

    /** Segundo registro cobraria o mesmo pedido duas vezes. */
    @Test
    void deveRejeitarPedidoQueJaTemPagamento() {
        Pedido pedido = pedidoDe("25.00");
        pedido.adicionarMeioPagamento(
                MeioPagamentoPedido.criar(MeioPagamento.PIX, Preco.of(new BigDecimal("25.00"))));
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        var meios = List.of(cartao("25.00"));

        assertThrows(ValidationException.class, () -> useCase.executar("ped-1", meios));

        verify(pedidoRepository, never()).salvar(any());
    }

    @Test
    void deveRejeitarPedidoJaFinalizado() {
        Pedido pedido = pedidoDe("25.00");
        pedido.atualizarStatus(StatusPedido.PREPARANDO);
        pedido.atualizarStatus(StatusPedido.PRONTO);
        pedido.atualizarStatus(StatusPedido.FINALIZADO);
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        var meios = List.of(cartao("25.00"));

        assertThrows(ValidationException.class, () -> useCase.executar("ped-1", meios));

        verify(pedidoRepository, never()).salvar(any());
    }

    @Test
    void deveRejeitarPedidoInexistente() {
        when(pedidoRepository.buscarPorId("nao-existe")).thenReturn(Optional.empty());
        var meios = List.of(cartao("25.00"));

        assertThrows(ValidationException.class, () -> useCase.executar("nao-existe", meios));
    }

    /** A auditoria e assincrona e nao-critica: falha nela nao pode derrubar o pagamento. */
    @Test
    void falhaNaAuditoriaNaoDerrubaOPagamento() {
        Pedido pedido = pedidoDe("25.00");
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        repositorioDevolveOProprioPedido();
        org.mockito.Mockito.doThrow(new RuntimeException("auditoria fora do ar"))
                .when(auditoriaPagamentoService).registrarPagamentoPosterior(any(), any());

        var resultado = useCase.executar("ped-1", List.of(cartao("25.00")));

        assertEquals(1, resultado.getMeiosPagamento().size());
    }
}
