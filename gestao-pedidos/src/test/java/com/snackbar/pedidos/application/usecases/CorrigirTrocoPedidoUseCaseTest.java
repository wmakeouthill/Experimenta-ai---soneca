package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.ItemPedido;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoPedido;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.valueobjects.NumeroPedido;

/**
 * Troco e dinheiro saindo da gaveta: um recalculo errado aqui devolve a mais ou a menos
 * para o cliente, sem passar por nenhuma adquirente que possa estornar depois.
 */
@ExtendWith(MockitoExtension.class)
class CorrigirTrocoPedidoUseCaseTest {

    @Mock private PedidoRepositoryPort pedidoRepository;

    @InjectMocks private CorrigirTrocoPedidoUseCase useCase;

    private Pedido pedidoDe(String valor, MeioPagamento meio) {
        Pedido pedido = Pedido.criarPedidoAutoAtendimento(NumeroPedido.de(1), "Cliente", "usr-1");
        pedido.adicionarItem(ItemPedido.criar("prod-1", "X-Burger", 1,
                Preco.of(new BigDecimal(valor)), null));
        pedido.adicionarMeioPagamento(MeioPagamentoPedido.criar(meio, Preco.of(new BigDecimal(valor))));
        return pedido;
    }

    private void repositorioDevolveOProprioPedido() {
        when(pedidoRepository.salvar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    private MeioPagamentoPedido dinheiroDe(Pedido pedido) {
        return pedido.getMeiosPagamento().stream()
                .filter(m -> m.getMeioPagamento() == MeioPagamento.DINHEIRO)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void deveRecalcularOTrocoComOValorRealEntregueEmNota() {
        Pedido pedido = pedidoDe("25.00", MeioPagamento.DINHEIRO);
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        repositorioDevolveOProprioPedido();

        useCase.executar("ped-1", new BigDecimal("50.00"));

        assertEquals(new BigDecimal("25.00"), dinheiroDe(pedido).getTroco().getAmount());
        assertEquals(new BigDecimal("50.00"), dinheiroDe(pedido).getValorPagoDinheiro().getAmount());
    }

    /** Valor pago igual ao pedido e caso legitimo: troco zero, nao erro. */
    @Test
    void deveAceitarValorPagoExatoComTrocoZero() {
        Pedido pedido = pedidoDe("25.00", MeioPagamento.DINHEIRO);
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        repositorioDevolveOProprioPedido();

        useCase.executar("ped-1", new BigDecimal("25.00"));

        assertEquals(0, dinheiroDe(pedido).getTroco().getAmount().compareTo(BigDecimal.ZERO));
    }

    /** Pagar menos que o pedido geraria troco negativo: a gaveta fecharia furada. */
    @Test
    void deveRejeitarValorPagoMenorQueOPedido() {
        Pedido pedido = pedidoDe("25.00", MeioPagamento.DINHEIRO);
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        var valorPago = new BigDecimal("20.00");

        assertThrows(ValidationException.class, () -> useCase.executar("ped-1", valorPago));

        verify(pedidoRepository, never()).salvar(any());
    }

    @Test
    void deveRejeitarPedidoSemPagamentoEmDinheiro() {
        Pedido pedido = pedidoDe("25.00", MeioPagamento.CARTAO_CREDITO);
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        var valorPago = new BigDecimal("50.00");

        assertThrows(ValidationException.class, () -> useCase.executar("ped-1", valorPago));

        verify(pedidoRepository, never()).salvar(any());
    }

    /** Pedido cancelado nao tem dinheiro para devolver: corrigir troco aqui inventa saida de caixa. */
    @Test
    void deveRejeitarPedidoCancelado() {
        Pedido pedido = pedidoDe("25.00", MeioPagamento.DINHEIRO);
        pedido.cancelar();
        when(pedidoRepository.buscarPorId("ped-1")).thenReturn(Optional.of(pedido));
        var valorPago = new BigDecimal("50.00");

        assertThrows(ValidationException.class, () -> useCase.executar("ped-1", valorPago));

        verify(pedidoRepository, never()).salvar(any());
    }

    @Test
    void deveRejeitarPedidoInexistente() {
        when(pedidoRepository.buscarPorId("nao-existe")).thenReturn(Optional.empty());
        var valorPago = new BigDecimal("50.00");

        assertThrows(ValidationException.class, () -> useCase.executar("nao-existe", valorPago));
    }
}
