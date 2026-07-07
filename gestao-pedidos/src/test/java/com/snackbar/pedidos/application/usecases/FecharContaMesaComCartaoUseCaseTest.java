package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.pedidos.application.dto.CartaoInputRequest;
import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;
import com.snackbar.pedidos.application.dto.FecharContaComCartaoRequest;
import com.snackbar.pedidos.application.dto.ResultadoContaCartaoDTO;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort.ResultadoPagamentoCartao;
import com.snackbar.pedidos.application.ports.ConfiguracaoPagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.ContaMesaRepositoryPort;
import com.snackbar.pedidos.application.ports.MesaRepositoryPort;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.services.AplicarPagamentoMesaAprovadoService;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.Mesa;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.StatusContaMesa;

@ExtendWith(MockitoExtension.class)
class FecharContaMesaComCartaoUseCaseTest {

    @Mock private PedidoRepositoryPort pedidoRepository;
    @Mock private ContaMesaRepositoryPort contaRepository;
    @Mock private CartaoDigitalGatewayPort cartaoGateway;
    @Mock private PagamentoRepositoryPort pagamentoRepository;
    @Mock private MesaRepositoryPort mesaRepository;
    @Mock private ConfiguracaoPagamentoRepositoryPort configuracaoRepository;
    @Mock private AplicarPagamentoMesaAprovadoService aplicarPagamentoAprovado;

    @InjectMocks private FecharContaMesaComCartaoUseCase useCase;

    private FecharContaComCartaoRequest request;

    @BeforeEach
    void setUp() {
        CartaoInputRequest cartao = new CartaoInputRequest();
        cartao.setNumero("5155901222280001");
        cartao.setNomePortador("ANA SILVA");
        cartao.setValidadeMes("12");
        cartao.setValidadeAno("2030");
        cartao.setCvv("123");
        cartao.setParcelas(1);

        request = new FecharContaComCartaoRequest();
        request.setMesaToken("token-1");
        request.setCartao(cartao);
    }

    @Test
    void deveAprovarCartaoEPagarConta() {
        configAtivo();
        Mesa mesa = mesa();
        Pedido pedido1 = pedido("p1", "30.00");
        Pedido pedido2 = pedido("p2", "20.00");
        when(mesaRepository.buscarPorQrCodeToken("token-1")).thenReturn(Optional.of(mesa));
        when(contaRepository.buscarAbertaPorMesaECliente("mesa-1", "cliente-1")).thenReturn(Optional.empty());
        when(pedidoRepository.buscarAbertosPorMesaESemPagamento("mesa-1", "cliente-1"))
                .thenReturn(List.of(pedido1, pedido2));
        when(contaRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cartaoGateway.gateway()).thenReturn(GatewayPagamento.SIMULADO);
        when(cartaoGateway.pagar(any())).thenReturn(
                new ResultadoPagamentoCartao(true, "pay-1", "VISA", "AUT1", null));
        when(pagamentoRepository.salvarImediato(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pagamentoRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        ResultadoContaCartaoDTO resultado = useCase.executar("corr-1", "cliente-1", request);

        assertTrue(resultado.aprovado());
        verify(aplicarPagamentoAprovado).aplicar(any(), eq(MeioPagamento.CARTAO_CREDITO));
    }

    @Test
    void deveRecusarCartaoECancelarConta() {
        configAtivo();
        Mesa mesa = mesa();
        Pedido pedido1 = pedido("p1", "30.00");
        when(mesaRepository.buscarPorQrCodeToken("token-1")).thenReturn(Optional.of(mesa));
        when(contaRepository.buscarAbertaPorMesaECliente("mesa-1", "cliente-1")).thenReturn(Optional.empty());
        when(pedidoRepository.buscarAbertosPorMesaESemPagamento("mesa-1", "cliente-1"))
                .thenReturn(List.of(pedido1));
        when(contaRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cartaoGateway.gateway()).thenReturn(GatewayPagamento.SIMULADO);
        when(cartaoGateway.pagar(any())).thenReturn(
                new ResultadoPagamentoCartao(false, null, null, null, "Cartao recusado"));
        when(pagamentoRepository.salvarImediato(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pagamentoRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        ResultadoContaCartaoDTO resultado = useCase.executar("corr-1", "cliente-1", request);

        assertFalse(resultado.aprovado());
        verify(contaRepository, org.mockito.Mockito.atLeastOnce()).salvar(
                org.mockito.ArgumentMatchers.argThat(c -> c.getStatus() == StatusContaMesa.CANCELADA));
    }

    private void configAtivo() {
        when(configuracaoRepository.buscar()).thenReturn(new ConfiguracaoPagamentoDTO(
                false, false, false, true, ModoPagamentoMesa.POS_PAGO));
    }

    private Mesa mesa() {
        Mesa mesa = org.mockito.Mockito.mock(Mesa.class);
        when(mesa.getId()).thenReturn("mesa-1");
        when(mesa.getNumero()).thenReturn(7);
        return mesa;
    }

    private Pedido pedido(String id, String valor) {
        Pedido pedido = org.mockito.Mockito.mock(Pedido.class);
        when(pedido.getId()).thenReturn(id);
        when(pedido.getValorTotal()).thenReturn(Preco.of(new BigDecimal(valor)));
        return pedido;
    }
}
