package com.snackbar.pedidos.application.usecases;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.services.AplicarPagamentoMesaAprovadoService;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.valueobjects.DadosPix;

@ExtendWith(MockitoExtension.class)
class ConfirmarPagamentoPixContaUseCaseTest {

    @Mock private PagamentoRepositoryPort pagamentoRepository;
    @Mock private AplicarPagamentoMesaAprovadoService aplicarPagamentoAprovado;

    @InjectMocks private ConfirmarPagamentoPixUseCase useCase;

    @Test
    void deveConfirmarPixEDelegarAplicacaoDaContaMesa() {
        Pagamento pagamento = Pagamento.iniciarParaContaMesa(
                "conta-1", CanalPagamento.MESA, GatewayPagamento.SIMULADO,
                5000L, MeioPagamentoGateway.PIX, "corr-1");
        pagamento.marcarAguardandoPix(new DadosPix(
                "txid-1", "p", "b", "c", LocalDateTime.now().plusMinutes(3)));

        when(pagamentoRepository.buscarPorTxidPix("txid-1")).thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.executar("txid-1", "E2E-1");

        verify(aplicarPagamentoAprovado).aplicar(pagamento, MeioPagamento.PIX);
        verify(pagamentoRepository).salvar(pagamento);
    }
}
