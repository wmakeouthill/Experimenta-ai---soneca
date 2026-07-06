package com.snackbar.pedidos.infrastructure.gateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort.DadosCartaoInput;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort.PagarCartaoCommand;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort.ResultadoPagamentoCartao;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;

class SimuladoCartaoDigitalAdapterTest {

    private final SimuladoCartaoDigitalAdapter adapter = new SimuladoCartaoDigitalAdapter();

    private PagarCartaoCommand comando(String numero) {
        return new PagarCartaoCommand("ref-1", "corr-1", 3000L,
                new DadosCartaoInput(numero, "ANA SILVA", "12", "2030", "123"), 1);
    }

    @Test
    void deveIdentificarGatewaySimulado() {
        org.junit.jupiter.api.Assertions.assertEquals(GatewayPagamento.SIMULADO, adapter.gateway());
    }

    @Test
    void deveAprovarCartaoValido() {
        ResultadoPagamentoCartao resultado = adapter.pagar(comando("5155901222280001"));
        assertTrue(resultado.aprovado());
        assertNotNull(resultado.gatewayPaymentId());
    }

    @Test
    void deveRecusarCartaoDeTesteTerminadoEm0000() {
        ResultadoPagamentoCartao resultado = adapter.pagar(comando("5155901222280000"));
        assertFalse(resultado.aprovado());
        assertNotNull(resultado.motivo());
    }

    @Test
    void toStringDeDadosCartaoNaoExpoePan() {
        String texto = new DadosCartaoInput("5155901222280001", "ANA", "12", "2030", "123").toString();
        org.junit.jupiter.api.Assertions.assertFalse(texto.contains("5155901222280001"));
        org.junit.jupiter.api.Assertions.assertFalse(texto.contains("123"));
    }
}
