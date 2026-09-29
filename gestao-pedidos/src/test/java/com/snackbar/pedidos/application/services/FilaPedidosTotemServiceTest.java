package com.snackbar.pedidos.application.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.kernel.domain.exceptions.BusinessRuleException;
import com.snackbar.pedidos.application.dto.CriarPedidoAutoAtendimentoRequest;
import com.snackbar.pedidos.application.ports.CardapioServicePort;
import com.snackbar.pedidos.application.ports.PedidoPendenteRepositoryPort;

@ExtendWith(MockitoExtension.class)
class FilaPedidosTotemServiceTest {

    @Mock
    private CardapioServicePort cardapioService;
    @Mock
    private PedidoPendenteRepositoryPort pedidoPendenteRepository;
    @Mock
    private ValidadorStatusLoja validadorStatusLoja;

    @InjectMocks
    private FilaPedidosTotemService service;

    @Test
    void lojaSemReceberPedidoNaoEntraNaFila() {
        when(validadorStatusLoja.exigirLojaAberta()).thenThrow(new BusinessRuleException("loja pausada"));
        CriarPedidoAutoAtendimentoRequest request = new CriarPedidoAutoAtendimentoRequest();
        request.setItens(List.of());

        assertThrows(BusinessRuleException.class, () -> service.adicionarPedido(request));
        verify(pedidoPendenteRepository, never()).salvar(any());
    }
}
