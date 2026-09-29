package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.kernel.domain.exceptions.BusinessRuleException;
import com.snackbar.pedidos.application.ports.CardapioServicePort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.services.AuditoriaPagamentoService;
import com.snackbar.pedidos.application.services.FilaPedidosTotemService;
import com.snackbar.pedidos.application.services.GeradorNumeroPedidoService;
import com.snackbar.pedidos.application.services.ValidadorStatusLoja;
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
}
