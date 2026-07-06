package com.snackbar.pedidos.application.usecases;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
class ConfirmarPagamentoPixMesaUseCaseTest {

    @Mock private PagamentoRepositoryPort pagamentoRepository;
    @Mock private AplicarPagamentoMesaAprovadoService aplicarPagamentoAprovado;

    @InjectMocks private ConfirmarPagamentoPixUseCase useCase;

    @Test
    void deveConfirmarPixEDelegarAplicacaoDoPedidoPendente() {
        var pagamento = aguardandoPixDePendente("pendente-1", "txid-1");
        when(pagamentoRepository.buscarPorTxidPix("txid-1")).thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.executar("txid-1", "E2E-1");

        verify(aplicarPagamentoAprovado).aplicar(pagamento, MeioPagamento.PIX);
        verify(pagamentoRepository).salvar(pagamento);
    }

    @Test
    void deveSerIdempotenteQuandoPagamentoJaFinalizado() {
        var pagamento = aguardandoPixDePendente("pendente-2", "txid-2");
        pagamento.aprovarPix("E2E-2");
        when(pagamentoRepository.buscarPorTxidPix("txid-2")).thenReturn(Optional.of(pagamento));

        useCase.executar("txid-2", "E2E-2");

        verify(aplicarPagamentoAprovado, never()).aplicar(any(), any());
        verify(pagamentoRepository, never()).salvar(any());
    }

    private Pagamento aguardandoPixDePendente(String pendenteId, String txid) {
        Pagamento pagamento = Pagamento.iniciarParaPedidoPendente(
                pendenteId, CanalPagamento.MESA, GatewayPagamento.SIMULADO,
                3000L, MeioPagamentoGateway.PIX, "corr-" + txid);
        pagamento.marcarAguardandoPix(new DadosPix(
                txid, "payload", "b64", "copia", LocalDateTime.now().plusMinutes(3)));
        return pagamento;
    }
}
