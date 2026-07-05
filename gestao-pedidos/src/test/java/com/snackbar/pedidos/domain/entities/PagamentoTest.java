package com.snackbar.pedidos.domain.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.domain.valueobjects.DadosPix;
import com.snackbar.pedidos.domain.valueobjects.DadosTef;

class PagamentoTest {

    private Pagamento novoPagamentoPix() {
        return Pagamento.iniciarParaPedido(
                "pedido-1", CanalPagamento.TOTEM, GatewayPagamento.SIMULADO,
                2500L, MeioPagamentoGateway.PIX, "corr-1");
    }

    private DadosPix dadosPix() {
        return new DadosPix("txid-1", "payload", "base64", "copiaecola",
                LocalDateTime.now().plusMinutes(3));
    }

    @Test
    void deveIniciarComStatusIniciadoQuandoDadosValidos() {
        Pagamento pagamento = novoPagamentoPix();
        assertEquals(StatusPagamento.INICIADO, pagamento.getStatus());
        assertEquals(CanalPagamento.TOTEM, pagamento.getCanal());
        assertEquals(GatewayPagamento.SIMULADO, pagamento.getGateway());
        assertEquals("pedido-1", pagamento.getPedidoId());
        assertNull(pagamento.getPedidoPendenteId());
        assertNull(pagamento.getContaMesaId());
        assertNotNull(pagamento.getIniciadoEm());
        assertFalse(pagamento.estaFinalizado());
    }

    @Test
    void deveIniciarParaPedidoPendenteQuandoCanalMesa() {
        Pagamento pagamento = Pagamento.iniciarParaPedidoPendente(
                "pendente-1", CanalPagamento.MESA, GatewayPagamento.GETNET,
                1000L, MeioPagamentoGateway.PIX, "corr-2");
        assertEquals("pendente-1", pagamento.getPedidoPendenteId());
        assertNull(pagamento.getPedidoId());
    }

    @Test
    void deveIniciarParaContaMesaQuandoPosPago() {
        Pagamento pagamento = Pagamento.iniciarParaContaMesa(
                "conta-1", CanalPagamento.MESA, GatewayPagamento.GETNET,
                4200L, MeioPagamentoGateway.PIX, "corr-3");
        assertEquals("conta-1", pagamento.getContaMesaId());
        assertNull(pagamento.getPedidoId());
    }

    @Test
    void deveLancarExcecaoQuandoValorZero() {
        assertThrows(ValidationException.class, () -> Pagamento.iniciarParaPedido(
                "pedido-1", CanalPagamento.TOTEM, GatewayPagamento.SIMULADO,
                0L, MeioPagamentoGateway.PIX, "corr-1"));
    }

    @Test
    void deveLancarExcecaoQuandoCanalNulo() {
        assertThrows(ValidationException.class, () -> Pagamento.iniciarParaPedido(
                "pedido-1", null, GatewayPagamento.SIMULADO,
                100L, MeioPagamentoGateway.PIX, "corr-1"));
    }

    @Test
    void deveLancarExcecaoQuandoGatewayNulo() {
        assertThrows(ValidationException.class, () -> Pagamento.iniciarParaPedido(
                "pedido-1", CanalPagamento.TOTEM, null,
                100L, MeioPagamentoGateway.PIX, "corr-1"));
    }

    @Test
    void deveMarcarAguardandoPixQuandoMeioPix() {
        Pagamento pagamento = novoPagamentoPix();
        pagamento.marcarAguardandoPix(dadosPix());
        assertEquals(StatusPagamento.AGUARDANDO_PIX, pagamento.getStatus());
        assertEquals("txid-1", pagamento.getPixTxid());
    }

    @Test
    void deveLancarExcecaoQuandoMarcarAguardandoPixComCartao() {
        Pagamento pagamento = Pagamento.iniciarParaPedido(
                "pedido-1", CanalPagamento.TOTEM, GatewayPagamento.SIMULADO,
                2500L, MeioPagamentoGateway.CARTAO_CREDITO, "corr-1");
        assertThrows(ValidationException.class, () -> pagamento.marcarAguardandoPix(dadosPix()));
    }

    @Test
    void deveAprovarPixQuandoAguardandoPix() {
        Pagamento pagamento = novoPagamentoPix();
        pagamento.marcarAguardandoPix(dadosPix());
        pagamento.aprovarPix("E2E123");
        assertEquals(StatusPagamento.APROVADO, pagamento.getStatus());
        assertEquals("E2E123", pagamento.getPixEndToEndId());
        assertTrue(pagamento.estaFinalizado());
        assertNotNull(pagamento.getFinalizadoEm());
    }

    @Test
    void deveLancarExcecaoQuandoAprovarPixSemAguardar() {
        Pagamento pagamento = novoPagamentoPix();
        assertThrows(ValidationException.class, () -> pagamento.aprovarPix("E2E123"));
    }

    @Test
    void deveAprovarTefQuandoAguardandoTef() {
        Pagamento pagamento = Pagamento.iniciarParaPedido(
                "pedido-1", CanalPagamento.TOTEM, GatewayPagamento.SIMULADO,
                2500L, MeioPagamentoGateway.CARTAO_DEBITO, "corr-1");
        pagamento.marcarAguardandoTef();
        pagamento.aprovarTef(new DadosTef("nsu", "VISA", "aut", "adq", "comprovante"));
        assertEquals(StatusPagamento.APROVADO, pagamento.getStatus());
        assertEquals("nsu", pagamento.getNsuTef());
    }

    @Test
    void deveExpirarQuandoNaoFinalizado() {
        Pagamento pagamento = novoPagamentoPix();
        pagamento.marcarAguardandoPix(dadosPix());
        pagamento.expirar();
        assertEquals(StatusPagamento.EXPIRADO, pagamento.getStatus());
        assertTrue(pagamento.estaFinalizado());
    }

    @Test
    void deveLancarExcecaoQuandoCancelarPagamentoFinalizado() {
        Pagamento pagamento = novoPagamentoPix();
        pagamento.marcarAguardandoPix(dadosPix());
        pagamento.aprovarPix("E2E123");
        assertThrows(ValidationException.class, () -> pagamento.cancelar("desistiu"));
    }

    @Test
    void deveDefinirGatewayPaymentId() {
        Pagamento pagamento = novoPagamentoPix();
        pagamento.definirGatewayPaymentId("pay-123");
        assertEquals("pay-123", pagamento.getGatewayPaymentId());
    }

    @Test
    void deveRestaurarTodosOsCampos() {
        LocalDateTime agora = LocalDateTime.now();
        Pagamento pagamento = Pagamento.restaurar(
                "id-1", CanalPagamento.MESA, GatewayPagamento.GETNET,
                null, "pendente-1", null,
                "corr-1", "pay-9", 990L,
                MeioPagamentoGateway.PIX, StatusPagamento.AGUARDANDO_PIX,
                null, null, null, null, null,
                "txid-9", "payload", "b64", "copia", null, agora.plusMinutes(3),
                null, agora, null, agora, agora, 2L);
        assertEquals("id-1", pagamento.getId());
        assertEquals(CanalPagamento.MESA, pagamento.getCanal());
        assertEquals(GatewayPagamento.GETNET, pagamento.getGateway());
        assertEquals("pay-9", pagamento.getGatewayPaymentId());
        assertEquals("pendente-1", pagamento.getPedidoPendenteId());
        assertEquals(2L, pagamento.getVersion());
    }
}
