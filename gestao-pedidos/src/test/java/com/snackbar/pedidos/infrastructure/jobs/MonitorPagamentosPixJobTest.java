package com.snackbar.pedidos.infrastructure.jobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort.ResultadoConsultaPix;
import com.snackbar.pedidos.application.ports.PixGatewayPort.StatusCobrancaPix;
import com.snackbar.pedidos.application.usecases.ConfirmarPagamentoPixUseCase;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.entities.StatusPagamento;
import com.snackbar.pedidos.domain.valueobjects.DadosPix;

@ExtendWith(MockitoExtension.class)
class MonitorPagamentosPixJobTest {

    @Mock
    private PagamentoRepositoryPort pagamentoRepository;

    @Mock
    private PixGatewayPort pixGateway;

    @Mock
    private ConfirmarPagamentoPixUseCase confirmarPixUseCase;

    @InjectMocks
    private MonitorPagamentosPixJob job;

    private Pagamento aguardandoPix(String txid, LocalDateTime expiracao) {
        Pagamento pagamento = Pagamento.iniciarParaPedido(
                "pedido-1", CanalPagamento.TOTEM, GatewayPagamento.GETNET,
                2500L, MeioPagamentoGateway.PIX, "corr-" + txid);
        pagamento.marcarAguardandoPix(new DadosPix(txid, "payload", "b64", "copia", expiracao));
        return pagamento;
    }

    @Test
    void deveConfirmarPagamentoQuandoGatewayInformaPago() {
        var pagamento = aguardandoPix("txid-1", LocalDateTime.now().plusMinutes(2));
        when(pagamentoRepository.buscarAguardandoPix()).thenReturn(List.of(pagamento));
        when(pagamentoRepository.buscarPendentesAnteriores(any())).thenReturn(List.of());
        when(pixGateway.consultarStatus("txid-1"))
                .thenReturn(new ResultadoConsultaPix(StatusCobrancaPix.PAGA, "E2E-9"));

        job.executar();

        verify(confirmarPixUseCase).executar("txid-1", "E2E-9");
    }

    @Test
    void deveUsarEndToEndSinteticoQuandoGatewayNaoInforma() {
        var pagamento = aguardandoPix("txid-2", LocalDateTime.now().plusMinutes(2));
        when(pagamentoRepository.buscarAguardandoPix()).thenReturn(List.of(pagamento));
        when(pagamentoRepository.buscarPendentesAnteriores(any())).thenReturn(List.of());
        when(pixGateway.consultarStatus("txid-2"))
                .thenReturn(new ResultadoConsultaPix(StatusCobrancaPix.PAGA, null));

        job.executar();

        verify(confirmarPixUseCase).executar("txid-2", "POLL-txid-2");
    }

    @Test
    void deveExpirarQuandoQrVencidoEGatewayNaoConfirmaPagamento() {
        var pagamento = aguardandoPix("txid-3", LocalDateTime.now().minusMinutes(5));
        when(pagamentoRepository.buscarAguardandoPix()).thenReturn(List.of(pagamento));
        when(pagamentoRepository.buscarPendentesAnteriores(any())).thenReturn(List.of());
        when(pixGateway.consultarStatus("txid-3"))
                .thenReturn(new ResultadoConsultaPix(StatusCobrancaPix.AGUARDANDO, null));

        job.executar();

        verify(confirmarPixUseCase, never()).executar(anyString(), anyString());
        verify(pagamentoRepository).salvar(argThat(salvo ->
                salvo.getStatus() == StatusPagamento.EXPIRADO));
    }

    @Test
    void deveExpirarPendentesAntigos() {
        var antigo = Pagamento.iniciarParaPedido(
                "pedido-2", CanalPagamento.TOTEM, GatewayPagamento.SIMULADO,
                1000L, MeioPagamentoGateway.CARTAO_CREDITO, "corr-antigo");
        antigo.marcarAguardandoTef();
        when(pagamentoRepository.buscarAguardandoPix()).thenReturn(List.of());
        when(pagamentoRepository.buscarPendentesAnteriores(any())).thenReturn(List.of(antigo));

        job.executar();

        verify(pagamentoRepository).salvar(argThat(salvo ->
                salvo.getStatus() == StatusPagamento.EXPIRADO
                        && "corr-antigo".equals(salvo.getCorrelationId())));
        verify(confirmarPixUseCase, never()).executar(anyString(), anyString());
        verify(pixGateway, never()).consultarStatus(eq("qualquer"));
    }
}
