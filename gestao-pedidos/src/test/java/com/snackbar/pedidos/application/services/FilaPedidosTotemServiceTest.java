package com.snackbar.pedidos.application.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.cardapio.application.dto.AdicionalDTO;
import com.snackbar.cardapio.application.dto.ProdutoDTO;
import com.snackbar.kernel.domain.exceptions.BusinessRuleException;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.CriarPedidoAutoAtendimentoRequest;
import com.snackbar.pedidos.application.dto.ItemPedidoAdicionalRequest;
import com.snackbar.pedidos.application.dto.ItemPedidoRequest;
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

    @Test
    void produtoDesativadoNaoEntraNaFilaEAMensagemCitaONome() {
        ProdutoDTO xBacon = ProdutoDTO.builder().nome("X-Bacon").disponivel(false).build();
        when(cardapioService.buscarProdutoPorId("p1")).thenReturn(xBacon);
        when(cardapioService.buscarProdutoDisponivel("p1")).thenCallRealMethod();
        CriarPedidoAutoAtendimentoRequest request = new CriarPedidoAutoAtendimentoRequest();
        request.setItens(List.of(new ItemPedidoRequest("p1", 1, null, null)));

        ValidationException erro = assertThrows(ValidationException.class, () -> service.adicionarPedido(request));

        assertEquals("Produto indisponível no momento: X-Bacon", erro.getMessage());
        verify(pedidoPendenteRepository, never()).salvar(any());
    }

    @Test
    void adicionalDesativadoNaoEntraNaFilaEAMensagemCitaONome() {
        ProdutoDTO xBurger = ProdutoDTO.builder().nome("X-Burger").preco(BigDecimal.TEN).disponivel(true).build();
        when(cardapioService.buscarProdutoDisponivel("p1")).thenReturn(xBurger);
        AdicionalDTO bacon = AdicionalDTO.builder().nome("Bacon extra").disponivel(false).build();
        when(cardapioService.buscarAdicionalPorId("a1")).thenReturn(bacon);
        when(cardapioService.buscarAdicionalDisponivel("a1")).thenCallRealMethod();
        CriarPedidoAutoAtendimentoRequest request = new CriarPedidoAutoAtendimentoRequest();
        request.setItens(List.of(new ItemPedidoRequest("p1", 1, null, List.of(new ItemPedidoAdicionalRequest("a1", 1)))));

        ValidationException erro = assertThrows(ValidationException.class, () -> service.adicionarPedido(request));

        assertEquals("Adicional indisponível no momento: Bacon extra", erro.getMessage());
        verify(pedidoPendenteRepository, never()).salvar(any());
    }
}
