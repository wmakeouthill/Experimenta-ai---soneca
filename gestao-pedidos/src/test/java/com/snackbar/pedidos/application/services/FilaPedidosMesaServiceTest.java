package com.snackbar.pedidos.application.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

import com.snackbar.kernel.domain.exceptions.BusinessRuleException;
import com.snackbar.pedidos.application.dto.CriarPedidoMesaRequest;
import com.snackbar.pedidos.application.ports.CardapioServicePort;
import com.snackbar.pedidos.application.ports.MesaRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoPendenteRepositoryPort;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import com.snackbar.pedidos.domain.entities.SessaoTrabalho;
import com.snackbar.pedidos.domain.exceptions.MesaNaoEncontradaException;

@ExtendWith(MockitoExtension.class)
class FilaPedidosMesaServiceTest {

    @Mock
    private MesaRepositoryPort mesaRepository;
    @Mock
    private CardapioServicePort cardapioService;
    @Mock
    private PedidoPendenteRepositoryPort pedidoPendenteRepository;
    @Mock
    private SessaoTrabalhoRepositoryPort sessaoTrabalhoRepository;

    @InjectMocks
    private FilaPedidosMesaService service;

    private CriarPedidoMesaRequest request() {
        CriarPedidoMesaRequest req = new CriarPedidoMesaRequest();
        req.setMesaToken("token-1");
        req.setClienteId("cliente-1");
        req.setNomeCliente("Ana");
        req.setItens(List.of());
        return req;
    }

    @Test
    void recusaPedidoComLojaFechada() {
        when(sessaoTrabalhoRepository.buscarSessaoAtiva()).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> service.adicionarPedido(request()));
        verify(pedidoPendenteRepository, never()).salvar(any());
    }

    @Test
    void recusaPedidoPrePagoComLojaPausadaAntesDeCobrar() {
        SessaoTrabalho pausada = SessaoTrabalho.criar(1, "usuario-1", BigDecimal.ZERO);
        pausada.pausar();
        when(sessaoTrabalhoRepository.buscarSessaoAtiva()).thenReturn(Optional.of(pausada));

        assertThrows(BusinessRuleException.class,
                () -> service.adicionarPedidoAguardandoPagamento(request(), "corr-1"));
        verify(pedidoPendenteRepository, never()).salvar(any());
    }

    @Test
    void lojaAbertaSegueParaValidarMesa() {
        when(sessaoTrabalhoRepository.buscarSessaoAtiva())
                .thenReturn(Optional.of(SessaoTrabalho.criar(1, "usuario-1", BigDecimal.ZERO)));
        when(mesaRepository.buscarPorQrCodeToken("token-1")).thenReturn(Optional.empty());

        assertThrows(MesaNaoEncontradaException.class, () -> service.adicionarPedido(request()));
    }
}
