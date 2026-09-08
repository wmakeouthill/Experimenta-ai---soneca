package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.CancelarPagamentoRequest;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.entities.StatusPagamento;
import com.snackbar.pedidos.domain.valueobjects.DadosTef;

@ExtendWith(MockitoExtension.class)
class CancelarPagamentoUseCaseTest {

    @Mock private PagamentoRepositoryPort pagamentoRepository;

    @InjectMocks private CancelarPagamentoUseCase useCase;

    private Pagamento aguardandoTef() {
        Pagamento pagamento = Pagamento.iniciarParaPedido("ped-1", CanalPagamento.TOTEM,
                GatewayPagamento.SIMULADO, 2500, MeioPagamentoGateway.CARTAO_CREDITO, "corr-1");
        pagamento.marcarAguardandoTef();
        return pagamento;
    }

    private Pagamento aprovado() {
        Pagamento pagamento = aguardandoTef();
        pagamento.aprovarTef(new DadosTef("37384", "MASTERCARD", "006299", "GETNET", "via"));
        return pagamento;
    }

    @Test
    void deveCancelarPagamentoAindaEmAberto() {
        when(pagamentoRepository.buscarPorCorrelationId("corr-1"))
                .thenReturn(Optional.of(aguardandoTef()));
        when(pagamentoRepository.salvar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        var resultado = useCase.executar(new CancelarPagamentoRequest("corr-1", "cliente desistiu"));

        assertEquals(StatusPagamento.CANCELADO, resultado.status());
        assertEquals("cliente desistiu", resultado.motivo());
    }

    /**
     * Dinheiro ja capturado nao se desfaz no banco: cancelar um pagamento APROVADO aqui
     * apagaria o registro da venda sem estornar nada na adquirente.
     */
    @Test
    void naoDeveDerrubarPagamentoJaAprovado() {
        Pagamento aprovado = aprovado();
        when(pagamentoRepository.buscarPorCorrelationId("corr-1")).thenReturn(Optional.of(aprovado));

        var resultado = useCase.executar(new CancelarPagamentoRequest("corr-1", "engano"));

        assertEquals(StatusPagamento.APROVADO, resultado.status());
        verify(pagamentoRepository, never()).salvar(any());
    }

    @Test
    void deveSerIdempotenteQuandoJaEstaCancelado() {
        Pagamento cancelado = aguardandoTef();
        cancelado.cancelar("primeiro cancelamento");
        when(pagamentoRepository.buscarPorCorrelationId("corr-1")).thenReturn(Optional.of(cancelado));

        var resultado = useCase.executar(new CancelarPagamentoRequest("corr-1", "segundo cancelamento"));

        assertEquals(StatusPagamento.CANCELADO, resultado.status());
        assertEquals("primeiro cancelamento", resultado.motivo());
        verify(pagamentoRepository, never()).salvar(any());
    }

    @Test
    void deveRejeitarCorrelationIdDesconhecido() {
        when(pagamentoRepository.buscarPorCorrelationId("nao-existe")).thenReturn(Optional.empty());
        var request = new CancelarPagamentoRequest("nao-existe", "qualquer");

        assertThrows(ValidationException.class, () -> useCase.executar(request));
    }
}
