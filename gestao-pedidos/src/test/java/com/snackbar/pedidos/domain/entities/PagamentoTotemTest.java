package com.snackbar.pedidos.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.domain.valueobjects.DadosPix;
import com.snackbar.pedidos.domain.valueobjects.DadosTef;

class PagamentoTotemTest {

    @Test
    void deveMarcarAguardandoTefQuandoCartaoIniciado() {
        PagamentoTotem pagamento = PagamentoTotem.iniciar(
                "pedido-1",
                4250,
                MeioPagamentoTotem.CARTAO_CREDITO,
                "corr-1");

        pagamento.marcarAguardandoTef();

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoTotem.AGUARDANDO_TEF);
    }

    @Test
    void deveAprovarTefQuandoDadosValidos() {
        PagamentoTotem pagamento = PagamentoTotem.iniciar(
                "pedido-1",
                4250,
                MeioPagamentoTotem.CARTAO_DEBITO,
                "corr-1");
        pagamento.marcarAguardandoTef();

        pagamento.aprovarTef(new DadosTef("123456", "VISA", "AUT-1", "127", "comprovante"));

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoTotem.APROVADO);
        assertThat(pagamento.getNsuTef()).isEqualTo("123456");
        assertThat(pagamento.getFinalizadoEm()).isNotNull();
    }

    @Test
    void deveAprovarPixQuandoWebhookInformaEndToEndId() {
        PagamentoTotem pagamento = PagamentoTotem.iniciar(
                "pedido-1",
                4250,
                MeioPagamentoTotem.PIX,
                "corr-1");
        pagamento.marcarAguardandoPix(new DadosPix(
                "txid-1",
                "payload",
                "base64",
                "copia-cola",
                LocalDateTime.now().plusMinutes(5)));

        pagamento.aprovarPix("E123");

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoTotem.APROVADO);
        assertThat(pagamento.getPixEndToEndId()).isEqualTo("E123");
    }

    @Test
    void deveBloquearCancelamentoQuandoPagamentoJaAprovado() {
        PagamentoTotem pagamento = PagamentoTotem.iniciar(
                "pedido-1",
                4250,
                MeioPagamentoTotem.CARTAO_CREDITO,
                "corr-1");
        pagamento.marcarAguardandoTef();
        pagamento.aprovarTef(new DadosTef("123456", null, null, null, null));

        assertThatThrownBy(() -> pagamento.cancelar("cliente desistiu"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("pagamento ja finalizado");
    }
}
