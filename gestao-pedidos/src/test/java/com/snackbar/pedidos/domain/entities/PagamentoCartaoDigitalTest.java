package com.snackbar.pedidos.domain.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.domain.valueobjects.DadosCartaoDigital;

class PagamentoCartaoDigitalTest {

    private Pagamento iniciarCartao() {
        return Pagamento.iniciarParaPedidoPendente(
                "pendente-1", CanalPagamento.MESA, GatewayPagamento.GETNET,
                3000L, MeioPagamentoGateway.CARTAO_CREDITO, "corr-1");
    }

    @Test
    void deveAprovarCartaoDigital() {
        Pagamento pagamento = iniciarCartao();
        pagamento.aprovarCartaoDigital(new DadosCartaoDigital("pay-123", "MASTERCARD", "AUT9"));

        assertEquals(StatusPagamento.APROVADO, pagamento.getStatus());
        assertEquals("pay-123", pagamento.getGatewayPaymentId());
        assertEquals("MASTERCARD", pagamento.getBandeira());
        assertEquals("AUT9", pagamento.getCodigoAutorizacao());
    }

    @Test
    void deveRejeitarAprovacaoQuandoMeioNaoECartao() {
        Pagamento pagamento = Pagamento.iniciarParaPedidoPendente(
                "pendente-1", CanalPagamento.MESA, GatewayPagamento.GETNET,
                3000L, MeioPagamentoGateway.PIX, "corr-1");
        assertThrows(ValidationException.class, () ->
                pagamento.aprovarCartaoDigital(new DadosCartaoDigital("pay-123", "VISA", "AUT")));
    }

    @Test
    void deveRejeitarAprovacaoDuplicada() {
        Pagamento pagamento = iniciarCartao();
        pagamento.aprovarCartaoDigital(new DadosCartaoDigital("pay-123", "VISA", "AUT"));
        assertThrows(ValidationException.class, () ->
                pagamento.aprovarCartaoDigital(new DadosCartaoDigital("pay-999", "VISA", "AUT")));
    }
}
