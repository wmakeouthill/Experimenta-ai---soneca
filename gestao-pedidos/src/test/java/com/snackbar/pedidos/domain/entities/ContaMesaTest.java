package com.snackbar.pedidos.domain.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.snackbar.kernel.domain.exceptions.ValidationException;

class ContaMesaTest {

    private ContaMesa abrir() {
        return ContaMesa.abrir("mesa-1", 7, "cliente-1",
                List.of("pedido-1", "pedido-2"), 5000L, "corr-1");
    }

    @Test
    void deveAbrirContaComStatusAberta() {
        ContaMesa conta = abrir();
        assertNotNull(conta.getId());
        assertEquals(StatusContaMesa.ABERTA, conta.getStatus());
        assertEquals(5000L, conta.getValorCentavos());
        assertEquals(2, conta.getPedidoIds().size());
        assertEquals("corr-1", conta.getCorrelationId());
    }

    @Test
    void deveRejeitarAberturaSemPedidos() {
        assertThrows(ValidationException.class, () ->
                ContaMesa.abrir("mesa-1", 7, "cliente-1", List.of(), 0L, "corr-1"));
    }

    @Test
    void devePagarContaAberta() {
        ContaMesa conta = abrir();
        conta.pagar();
        assertEquals(StatusContaMesa.PAGA, conta.getStatus());
    }

    @Test
    void deveSerIdempotenteAoPagarContaJaPaga() {
        ContaMesa conta = abrir();
        conta.pagar();
        conta.pagar();
        assertEquals(StatusContaMesa.PAGA, conta.getStatus());
    }

    @Test
    void deveRejeitarPagamentoDeContaCancelada() {
        ContaMesa conta = abrir();
        conta.cancelar();
        assertThrows(ValidationException.class, conta::pagar);
    }

    @Test
    void deveExporListaImutavelDePedidos() {
        ContaMesa conta = abrir();
        assertThrows(UnsupportedOperationException.class, () -> conta.getPedidoIds().add("x"));
        assertTrue(conta.getPedidoIds().contains("pedido-1"));
    }
}
