package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoPendenteRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.valueobjects.DadosPix;

@ExtendWith(MockitoExtension.class)
class ConfirmarPagamentoPixMesaUseCaseTest {

    @Mock
    private PagamentoRepositoryPort pagamentoRepository;

    @Mock
    private PedidoRepositoryPort pedidoRepository;

    @Mock
    private PedidoPendenteRepositoryPort pedidoPendenteRepository;

    @InjectMocks
    private ConfirmarPagamentoPixUseCase useCase;

    private Pagamento aguardandoPixDePendente(String pendenteId, String txid) {
        Pagamento pagamento = Pagamento.iniciarParaPedidoPendente(
                pendenteId, CanalPagamento.MESA, GatewayPagamento.SIMULADO,
                3000L, MeioPagamentoGateway.PIX, "corr-" + txid);
        pagamento.marcarAguardandoPix(new DadosPix(
                txid, "payload", "b64", "copia", LocalDateTime.now().plusMinutes(3)));
        return pagamento;
    }

    @Test
    void deveLiberarPedidoPendenteAoConfirmarPixDeMesa() {
        var pagamento = aguardandoPixDePendente("pendente-1", "txid-1");
        when(pagamentoRepository.buscarPorTxidPix("txid-1")).thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.executar("txid-1", "E2E-1");

        verify(pedidoPendenteRepository).liberarPagamento("pendente-1");
    }

    @Test
    void deveSerIdempotenteQuandoPagamentoJaFinalizado() {
        var pagamento = aguardandoPixDePendente("pendente-2", "txid-2");
        pagamento.aprovarPix("E2E-2");
        when(pagamentoRepository.buscarPorTxidPix("txid-2")).thenReturn(Optional.of(pagamento));

        useCase.executar("txid-2", "E2E-2");

        verify(pedidoPendenteRepository, org.mockito.Mockito.never()).liberarPagamento(any());
    }

    @Test
    void deveRejeitarQuandoTxidInexistente() {
        when(pagamentoRepository.buscarPorTxidPix("inexistente")).thenReturn(Optional.empty());

        assertThrows(ValidationException.class, () -> useCase.executar("inexistente", "E2E"));
    }
}
