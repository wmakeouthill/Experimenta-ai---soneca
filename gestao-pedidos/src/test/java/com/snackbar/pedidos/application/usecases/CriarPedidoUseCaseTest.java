package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.snackbar.cardapio.application.dto.ProdutoDTO;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.CriarPedidoRequest;
import com.snackbar.pedidos.application.dto.ItemPedidoRequest;
import com.snackbar.pedidos.application.ports.CardapioServicePort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.services.AuditoriaPagamentoService;
import com.snackbar.pedidos.application.services.GeradorNumeroPedidoService;
import com.snackbar.pedidos.application.services.ValidadorStatusLoja;
import com.snackbar.pedidos.domain.entities.SessaoTrabalho;
import com.snackbar.pedidos.domain.services.PedidoValidator;

@ExtendWith(MockitoExtension.class)
class CriarPedidoUseCaseTest {

    @Mock
    private PedidoRepositoryPort pedidoRepository;
    @Mock
    private CardapioServicePort cardapioService;
    @Mock
    private PedidoValidator pedidoValidator;
    @Mock
    private ValidadorStatusLoja validadorStatusLoja;
    @Mock
    private GeradorNumeroPedidoService geradorNumeroPedido;
    @Mock
    private AuditoriaPagamentoService auditoriaPagamentoService;

    @InjectMocks
    private CriarPedidoUseCase useCase;

    @Test
    void produtoRecusadoNaoConsomeNumeroDoPedido() {
        when(validadorStatusLoja.exigirSessaoAtiva()).thenReturn(SessaoTrabalho.criar(1, "usuario-1", BigDecimal.ZERO));
        when(cardapioService.buscarProdutoPorId("p1"))
                .thenReturn(ProdutoDTO.builder().nome("Bauru").disponivel(false).build());
        when(cardapioService.buscarProdutoDisponivel("p1")).thenCallRealMethod();
        CriarPedidoRequest request = new CriarPedidoRequest();
        request.setItens(List.of(new ItemPedidoRequest("p1", 1, null, null)));

        assertThrows(ValidationException.class, () -> useCase.executar(request));
        verify(geradorNumeroPedido, never()).gerarProximoNumero();
    }
}
