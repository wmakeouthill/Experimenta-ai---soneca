package com.snackbar.pedidos.application.ports;

import com.snackbar.pedidos.domain.entities.GatewayPagamento;

/**
 * Porta de gateway de cartao digital (credito). O adapter encapsula
 * tokenizacao + cobranca; o caso de uso nunca ve o PAN apos esta chamada.
 */
public interface CartaoDigitalGatewayPort {

    GatewayPagamento gateway();

    ResultadoPagamentoCartao pagar(PagarCartaoCommand command);

    record PagarCartaoCommand(
            String referencia,
            String correlationId,
            long valorCentavos,
            DadosCartaoInput cartao,
            int parcelas) {
    }

    /**
     * Dados sensiveis do cartao. toString() e redigido para impedir vazamento
     * de PAN/CVV em logs.
     */
    record DadosCartaoInput(
            String numero,
            String nomePortador,
            String validadeMes,
            String validadeAno,
            String cvv) {

        @Override
        public String toString() {
            String ultimos = (numero != null && numero.length() >= 4)
                    ? numero.substring(numero.length() - 4) : "****";
            return "DadosCartaoInput[final=" + ultimos + ", portador=" + nomePortador + "]";
        }
    }

    record ResultadoPagamentoCartao(
            boolean aprovado,
            String gatewayPaymentId,
            String bandeira,
            String codigoAutorizacao,
            String motivo) {
    }
}
