package com.snackbar.pedidos.infrastructure.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.snackbar.pedidos.application.usecases.*;
import com.snackbar.pedidos.infrastructure.idempotency.IdempotencyService;

@ExtendWith(MockitoExtension.class)
class PedidoRestControllerTest {

    @Mock private CriarPedidoUseCase criarPedido;
    @Mock private ListarPedidosUseCase listarPedidos;
    @Mock private BuscarPedidoPorIdUseCase buscarPedidoPorId;
    @Mock private AtualizarStatusPedidoUseCase atualizarStatusPedido;
    @Mock private CancelarPedidoUseCase cancelarPedido;
    @Mock private ExcluirPedidoUseCase excluirPedido;
    @Mock private RegistrarPagamentoPedidoUseCase registrarPagamentoPedido;
    @Mock private CorrigirTrocoPedidoUseCase corrigirTrocoPedido;
    @Mock private AtualizarPisoPedidoUseCase atualizarPisoPedido;
    @Mock private IdempotencyService idempotencyService;

    @InjectMocks private PedidoRestController controller;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listarSemFiltroOuSoComStatusRetorna400() throws Exception {
        mockMvc().perform(get("/api/pedidos")).andExpect(status().isBadRequest());
        mockMvc().perform(get("/api/pedidos").param("status", "PENDENTE")).andExpect(status().isBadRequest());

        verifyNoInteractions(listarPedidos);
    }

    @Test
    void listarPorSessao() throws Exception {
        when(listarPedidos.executarPorSessaoId("s1")).thenReturn(List.of());

        mockMvc().perform(get("/api/pedidos").param("sessaoId", "s1")).andExpect(status().isOk());
    }
}
