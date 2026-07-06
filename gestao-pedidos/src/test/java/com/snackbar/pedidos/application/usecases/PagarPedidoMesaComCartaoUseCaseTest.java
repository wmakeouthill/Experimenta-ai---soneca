package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.CartaoInputRequest;
import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;
import com.snackbar.pedidos.application.dto.CriarPedidoMesaRequest;
import com.snackbar.pedidos.application.dto.PagarPedidoMesaComCartaoRequest;
import com.snackbar.pedidos.application.dto.PedidoPendenteDTO;
import com.snackbar.pedidos.application.dto.ResultadoPagamentoCartaoDTO;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort.ResultadoPagamentoCartao;
import com.snackbar.pedidos.application.ports.ConfiguracaoPagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoPendenteRepositoryPort;
import com.snackbar.pedidos.application.services.AplicarPagamentoMesaAprovadoService;
import com.snackbar.pedidos.application.services.FilaPedidosMesaService;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;

@ExtendWith(MockitoExtension.class)
class PagarPedidoMesaComCartaoUseCaseTest {

    @Mock private FilaPedidosMesaService filaPedidosMesa;
    @Mock private CartaoDigitalGatewayPort cartaoGateway;
    @Mock private PagamentoRepositoryPort pagamentoRepository;
    @Mock private PedidoPendenteRepositoryPort pedidoPendenteRepository;
    @Mock private ConfiguracaoPagamentoRepositoryPort configuracaoRepository;
    @Mock private AplicarPagamentoMesaAprovadoService aplicarPagamentoAprovado;

    @InjectMocks private PagarPedidoMesaComCartaoUseCase useCase;

    private PagarPedidoMesaComCartaoRequest request;

    @BeforeEach
    void setUp() {
        CriarPedidoMesaRequest pedido = new CriarPedidoMesaRequest();
        pedido.setMesaToken("token-1");
        pedido.setClienteId("cliente-1");
        pedido.setNomeCliente("Ana");

        CartaoInputRequest cartao = new CartaoInputRequest();
        cartao.setNumero("5155901222280001");
        cartao.setNomePortador("ANA SILVA");
        cartao.setValidadeMes("12");
        cartao.setValidadeAno("2030");
        cartao.setCvv("123");
        cartao.setParcelas(1);

        request = new PagarPedidoMesaComCartaoRequest();
        request.setPedido(pedido);
        request.setCartao(cartao);
    }

    @Test
    void deveAprovarCartaoELiberarPedido() {
        configCartaoMesaAtivo();
        when(pagamentoRepository.buscarPorCorrelationId(anyString())).thenReturn(Optional.empty());
        when(filaPedidosMesa.adicionarPedidoAguardandoPagamento(any(), eq("corr-1"), eq(MeioPagamento.CARTAO_CREDITO)))
                .thenReturn(pendente());
        when(cartaoGateway.gateway()).thenReturn(GatewayPagamento.SIMULADO);
        when(cartaoGateway.pagar(any())).thenReturn(
                new ResultadoPagamentoCartao(true, "pay-1", "MASTERCARD", "AUT1", null));
        when(pagamentoRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        ResultadoPagamentoCartaoDTO resultado = useCase.executar("corr-1", request);

        assertTrue(resultado.aprovado());
        verify(aplicarPagamentoAprovado).aplicar(any(), eq(MeioPagamento.CARTAO_CREDITO));
        verify(pedidoPendenteRepository, never()).remover(anyString());
    }

    @Test
    void deveRecusarCartaoERemoverPendente() {
        configCartaoMesaAtivo();
        when(pagamentoRepository.buscarPorCorrelationId(anyString())).thenReturn(Optional.empty());
        when(filaPedidosMesa.adicionarPedidoAguardandoPagamento(any(), eq("corr-1"), eq(MeioPagamento.CARTAO_CREDITO)))
                .thenReturn(pendente());
        when(cartaoGateway.gateway()).thenReturn(GatewayPagamento.SIMULADO);
        when(cartaoGateway.pagar(any())).thenReturn(
                new ResultadoPagamentoCartao(false, null, null, null, "Cartao recusado"));
        when(pagamentoRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        ResultadoPagamentoCartaoDTO resultado = useCase.executar("corr-1", request);

        assertFalse(resultado.aprovado());
        verify(pedidoPendenteRepository).remover("pendente-1");
        verify(aplicarPagamentoAprovado, never()).aplicar(any(), any());
    }

    @Test
    void deveRejeitarQuandoCartaoMesaDesativado() {
        when(configuracaoRepository.buscar()).thenReturn(new ConfiguracaoPagamentoDTO(
                false, false, false, false, ModoPagamentoMesa.PRE_PAGO));

        assertThrows(ValidationException.class, () -> useCase.executar("corr-1", request));

        verify(filaPedidosMesa, never()).adicionarPedidoAguardandoPagamento(any(), anyString(), any());
    }

    private void configCartaoMesaAtivo() {
        when(configuracaoRepository.buscar()).thenReturn(new ConfiguracaoPagamentoDTO(
                false, false, false, true, ModoPagamentoMesa.PRE_PAGO));
    }

    private PedidoPendenteDTO pendente() {
        return PedidoPendenteDTO.builder()
                .id("pendente-1")
                .numeroMesa(7)
                .valorTotal(new BigDecimal("30.00"))
                .aguardandoPagamento(true)
                .build();
    }
}
