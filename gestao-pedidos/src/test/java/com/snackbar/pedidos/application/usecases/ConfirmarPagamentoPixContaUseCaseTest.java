package com.snackbar.pedidos.application.usecases;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

import com.snackbar.pedidos.application.ports.ContaMesaRepositoryPort;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoPendenteRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.ContaMesa;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.StatusContaMesa;
import com.snackbar.pedidos.domain.valueobjects.DadosPix;

@ExtendWith(MockitoExtension.class)
class ConfirmarPagamentoPixContaUseCaseTest {

    @Mock private PagamentoRepositoryPort pagamentoRepository;
    @Mock private PedidoRepositoryPort pedidoRepository;
    @Mock private PedidoPendenteRepositoryPort pedidoPendenteRepository;
    @Mock private ContaMesaRepositoryPort contaRepository;

    @InjectMocks private ConfirmarPagamentoPixUseCase useCase;

    @Test
    void deveMarcarContaPagaECarimbarPedidos() {
        Pagamento pagamento = Pagamento.iniciarParaContaMesa(
                "conta-1", CanalPagamento.MESA, GatewayPagamento.SIMULADO,
                5000L, MeioPagamentoGateway.PIX, "corr-1");
        pagamento.marcarAguardandoPix(new DadosPix("txid-1", "p", "b", "c", LocalDateTime.now().plusMinutes(3)));

        ContaMesa conta = ContaMesa.abrir("mesa-1", 7, "cliente-1",
                List.of("p1", "p2"), 5000L, "corr-1");

        Pedido p1 = org.mockito.Mockito.mock(Pedido.class);
        Pedido p2 = org.mockito.Mockito.mock(Pedido.class);
        when(p1.getValorTotal()).thenReturn(
                com.snackbar.cardapio.domain.valueobjects.Preco.of(new BigDecimal("30.00")));
        when(p2.getValorTotal()).thenReturn(
                com.snackbar.cardapio.domain.valueobjects.Preco.of(new BigDecimal("20.00")));

        when(pagamentoRepository.buscarPorTxidPix("txid-1")).thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contaRepository.buscarPorId("conta-1")).thenReturn(Optional.of(conta));
        when(contaRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pedidoRepository.buscarPorId("p1")).thenReturn(Optional.of(p1));
        when(pedidoRepository.buscarPorId("p2")).thenReturn(Optional.of(p2));

        useCase.executar("txid-1", "E2E-1");

        verify(contaRepository).salvar(org.mockito.ArgumentMatchers.argThat(
                c -> c.getStatus() == StatusContaMesa.PAGA));
        verify(pedidoRepository).salvar(p1);
        verify(pedidoRepository).salvar(p2);
    }
}
