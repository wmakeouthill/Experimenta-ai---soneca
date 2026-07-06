package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;
import com.snackbar.pedidos.application.dto.CriarPedidoMesaRequest;
import com.snackbar.pedidos.application.dto.PedidoPendenteDTO;
import com.snackbar.pedidos.application.ports.ConfiguracaoPagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort.CobrancaPixCriada;
import com.snackbar.pedidos.application.services.FilaPedidosMesaService;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;

@ExtendWith(MockitoExtension.class)
class CriarPedidoMesaComPixUseCaseTest {

    @Mock
    private FilaPedidosMesaService filaPedidosMesa;
    @Mock
    private PixGatewayPort pixGateway;
    @Mock
    private PagamentoRepositoryPort pagamentoRepository;
    @Mock
    private ConfiguracaoPagamentoRepositoryPort configuracaoRepository;

    @InjectMocks
    private CriarPedidoMesaComPixUseCase useCase;

    private CriarPedidoMesaRequest request() {
        CriarPedidoMesaRequest req = new CriarPedidoMesaRequest();
        req.setMesaToken("token-1");
        req.setClienteId("cliente-1");
        req.setNomeCliente("Ana");
        return req;
    }

    private PedidoPendenteDTO pendente() {
        return PedidoPendenteDTO.builder()
                .id("pendente-1")
                .valorTotal(new BigDecimal("30.00"))
                .dataHoraSolicitacao(LocalDateTime.now())
                .aguardandoPagamento(true)
                .build();
    }

    @Test
    void deveCriarPedidoOcultoEIniciarCobrancaPix() {
        when(configuracaoRepository.buscar()).thenReturn(new ConfiguracaoPagamentoDTO(
                false, false, true, false, ModoPagamentoMesa.PRE_PAGO));
        when(filaPedidosMesa.adicionarPedidoAguardandoPagamento(any(), anyString()))
                .thenReturn(pendente());
        when(pixGateway.gateway()).thenReturn(GatewayPagamento.SIMULADO);
        when(pixGateway.criarCobrancaDinamica(any())).thenReturn(new CobrancaPixCriada(
                "txid-1", "payload", "b64", "copia", LocalDateTime.now().plusMinutes(3)));
        when(pagamentoRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = useCase.executar("corr-1", request());

        assertEquals("pendente-1", resultado.pedido().getId());
        assertNotNull(resultado.pagamento());
        assertEquals("copia", resultado.pagamento().copiaECola());
    }

    @Test
    void deveRejeitarQuandoPixMesaDesativado() {
        when(configuracaoRepository.buscar()).thenReturn(new ConfiguracaoPagamentoDTO(
                false, false, false, false, ModoPagamentoMesa.PRE_PAGO));
        assertThrows(ValidationException.class, () -> useCase.executar("corr-1", request()));
    }

    @Test
    void deveRejeitarQuandoModoNaoEPrePago() {
        when(configuracaoRepository.buscar()).thenReturn(new ConfiguracaoPagamentoDTO(
                false, false, true, false, ModoPagamentoMesa.POS_PAGO));
        assertThrows(ValidationException.class, () -> useCase.executar("corr-1", request()));
    }
}
