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
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.services.AuditoriaPagamentoService;
import com.snackbar.pedidos.application.services.FilaPedidosMesaService;
import com.snackbar.pedidos.application.services.GeradorNumeroPedidoService;
import com.snackbar.pedidos.application.services.ValidadorStatusLoja;

@ExtendWith(MockitoExtension.class)
class AceitarPedidoMesaUseCaseTest {

    @Mock
    private FilaPedidosMesaService filaPedidosMesa;
    @Mock
    private PedidoRepositoryPort pedidoRepository;
    @Mock
    private ValidadorStatusLoja validadorStatusLoja;
    @Mock
    private GeradorNumeroPedidoService geradorNumeroPedido;
    @Mock
    private AuditoriaPagamentoService auditoriaPagamentoService;

    @InjectMocks
    private AceitarPedidoMesaUseCase useCase;

    @Test
    void semSessaoRecusaENaoTiraDaFila() {
        when(validadorStatusLoja.exigirSessaoAtiva()).thenThrow(new BusinessRuleException("sem sessão"));

        assertThrows(BusinessRuleException.class, () -> useCase.executar("pendente-1", "usuario-1"));
        verify(filaPedidosMesa, never()).buscarERemoverAtomicamente(anyString());
    }
}
