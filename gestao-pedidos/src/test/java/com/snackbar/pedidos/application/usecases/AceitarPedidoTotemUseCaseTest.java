package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.snackbar.cardapio.application.dto.AdicionalDTO;
import com.snackbar.kernel.domain.exceptions.BusinessRuleException;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.AdicionalPedidoPendenteDTO;
import com.snackbar.pedidos.application.dto.ItemPedidoPendenteDTO;
import com.snackbar.pedidos.application.dto.PedidoPendenteDTO;
import com.snackbar.pedidos.application.ports.CardapioServicePort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.services.AuditoriaPagamentoService;
import com.snackbar.pedidos.application.services.FilaPedidosTotemService;
import com.snackbar.pedidos.application.services.GeradorNumeroPedidoService;
import com.snackbar.pedidos.application.services.ValidadorStatusLoja;
import com.snackbar.pedidos.domain.entities.SessaoTrabalho;
import com.snackbar.pedidos.domain.services.PedidoValidator;

@ExtendWith(MockitoExtension.class)
class AceitarPedidoTotemUseCaseTest {

    @Mock
    private FilaPedidosTotemService filaPedidosTotem;
    @Mock
    private PedidoRepositoryPort pedidoRepository;
    @Mock
    private ValidadorStatusLoja validadorStatusLoja;
    @Mock
    private GeradorNumeroPedidoService geradorNumeroPedido;
    @Mock
    private PedidoValidator pedidoValidator;
    @Mock
    private AuditoriaPagamentoService auditoriaPagamentoService;
    @Mock
    private CardapioServicePort cardapioService;

    @InjectMocks
    private AceitarPedidoTotemUseCase useCase;

    @Test
    void semSessaoRecusaENaoTiraDaFila() {
        when(validadorStatusLoja.exigirSessaoAtiva()).thenThrow(new BusinessRuleException("sem sessão"));

        assertThrows(BusinessRuleException.class, () -> useCase.executar("pendente-1", "usuario-1"));
        verify(filaPedidosTotem, never()).buscarERemoverAtomicamente(anyString());
    }

    @Test
    void adicionalDesativadoDepoisDeEntrarNaFilaRecusaOAceite() {
        when(validadorStatusLoja.exigirSessaoAtiva()).thenReturn(SessaoTrabalho.criar(1, "usuario-1", BigDecimal.ZERO));
        AdicionalPedidoPendenteDTO bacon = AdicionalPedidoPendenteDTO.builder().adicionalId("a1").quantidade(1).build();
        ItemPedidoPendenteDTO item = ItemPedidoPendenteDTO.builder().produtoId("p1").adicionais(List.of(bacon)).build();
        when(filaPedidosTotem.buscarERemoverAtomicamente("pendente-1"))
                .thenReturn(Optional.of(PedidoPendenteDTO.builder().itens(List.of(item)).build()));
        when(cardapioService.buscarAdicionalPorId("a1"))
                .thenReturn(AdicionalDTO.builder().nome("Bacon").disponivel(false).build());
        when(cardapioService.buscarAdicionalDisponivel("a1")).thenCallRealMethod();

        ValidationException erro = assertThrows(ValidationException.class,
                () -> useCase.executar("pendente-1", "usuario-1"));

        assertEquals("Adicional indisponível no momento: Bacon", erro.getMessage());
        verify(geradorNumeroPedido, never()).gerarProximoNumero();
        verify(pedidoRepository, never()).salvar(any());
    }
}
