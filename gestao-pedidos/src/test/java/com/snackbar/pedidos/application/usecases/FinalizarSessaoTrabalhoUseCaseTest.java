package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.snackbar.pedidos.application.ports.ObterNomeUsuarioPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import com.snackbar.pedidos.application.services.FilaPedidosMesaService;
import com.snackbar.pedidos.application.services.FilaPedidosTotemService;
import com.snackbar.pedidos.domain.entities.SessaoTrabalho;

@ExtendWith(MockitoExtension.class)
class FinalizarSessaoTrabalhoUseCaseTest {

    @Mock
    private SessaoTrabalhoRepositoryPort repository;
    @Mock
    private PedidoRepositoryPort pedidoRepository;
    @Mock
    private ObterNomeUsuarioPort obterNomeUsuarioPort;
    @Mock
    private FilaPedidosMesaService filaPedidosMesa;
    @Mock
    private FilaPedidosTotemService filaPedidosTotem;

    @InjectMocks
    private FinalizarSessaoTrabalhoUseCase useCase;

    @Test
    void pedidoNaFilaDeAceiteImpedeFinalizar() {
        when(repository.buscarPorId("s1")).thenReturn(Optional.of(SessaoTrabalho.criar(1, "usuario-1", BigDecimal.ZERO)));
        when(pedidoRepository.buscarPorSessaoId("s1")).thenReturn(List.of());
        when(filaPedidosMesa.quantidadePedidosPendentes()).thenReturn(0);
        when(filaPedidosTotem.quantidadePedidosPendentes()).thenReturn(1);

        BusinessRuleException erro = assertThrows(BusinessRuleException.class,
                () -> useCase.executar("s1", BigDecimal.TEN));

        assertTrue(erro.getMessage().contains("1 na fila de aceite"));
        verify(repository, never()).salvar(any());
    }
}
