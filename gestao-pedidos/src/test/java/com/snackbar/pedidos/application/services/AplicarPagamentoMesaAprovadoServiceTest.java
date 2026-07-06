package com.snackbar.pedidos.application.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import com.snackbar.pedidos.application.ports.ContaMesaRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoPendenteRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.ContaMesa;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.StatusContaMesa;

@ExtendWith(MockitoExtension.class)
class AplicarPagamentoMesaAprovadoServiceTest {

    @Mock private PedidoRepositoryPort pedidoRepository;
    @Mock private PedidoPendenteRepositoryPort pedidoPendenteRepository;
    @Mock private ContaMesaRepositoryPort contaMesaRepository;

    @InjectMocks private AplicarPagamentoMesaAprovadoService service;

    @Test
    void deveLiberarPedidoPendente() {
        Pagamento pagamento = Pagamento.iniciarParaPedidoPendente(
                "pendente-1", CanalPagamento.MESA, GatewayPagamento.SIMULADO,
                3000L, MeioPagamentoGateway.CARTAO_CREDITO, "corr-1");

        service.aplicar(pagamento, MeioPagamento.CARTAO_CREDITO);

        verify(pedidoPendenteRepository).liberarPagamento("pendente-1");
        verify(contaMesaRepository, never()).salvar(any());
    }

    @Test
    void devePagarContaECarimbarPedidos() {
        Pagamento pagamento = Pagamento.iniciarParaContaMesa(
                "conta-1", CanalPagamento.MESA, GatewayPagamento.SIMULADO,
                5000L, MeioPagamentoGateway.CARTAO_CREDITO, "corr-1");
        ContaMesa conta = ContaMesa.abrir("mesa-1", 7, "cliente-1",
                List.of("p1", "p2"), 5000L, "corr-1");

        Pedido p1 = org.mockito.Mockito.mock(Pedido.class);
        Pedido p2 = org.mockito.Mockito.mock(Pedido.class);
        when(p1.getMeiosPagamento()).thenReturn(List.of());
        when(p2.getMeiosPagamento()).thenReturn(List.of());
        when(p1.getValorTotal()).thenReturn(Preco.of(new BigDecimal("30.00")));
        when(p2.getValorTotal()).thenReturn(Preco.of(new BigDecimal("20.00")));

        when(contaMesaRepository.buscarPorId("conta-1")).thenReturn(Optional.of(conta));
        when(contaMesaRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pedidoRepository.buscarPorId("p1")).thenReturn(Optional.of(p1));
        when(pedidoRepository.buscarPorId("p2")).thenReturn(Optional.of(p2));

        service.aplicar(pagamento, MeioPagamento.CARTAO_CREDITO);

        verify(contaMesaRepository).salvar(argThat(c -> c.getStatus() == StatusContaMesa.PAGA));
        verify(pedidoRepository).salvar(p1);
        verify(pedidoRepository).salvar(p2);
    }

    @Test
    void deveCarimbarPedidoDoTotem() {
        Pagamento pagamento = Pagamento.iniciarParaPedido(
                "pedido-1", CanalPagamento.TOTEM, GatewayPagamento.SIMULADO,
                2500L, MeioPagamentoGateway.PIX, "corr-1");
        Pedido pedido = org.mockito.Mockito.mock(Pedido.class);
        when(pedido.getMeiosPagamento()).thenReturn(List.of());
        when(pedidoRepository.buscarPorId("pedido-1")).thenReturn(Optional.of(pedido));

        service.aplicar(pagamento, MeioPagamento.PIX);

        verify(pedidoRepository).salvar(pedido);
    }
}
