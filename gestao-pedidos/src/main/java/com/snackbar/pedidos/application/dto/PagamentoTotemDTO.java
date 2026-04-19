package com.snackbar.pedidos.application.dto;

import java.time.LocalDateTime;

import com.snackbar.pedidos.domain.entities.MeioPagamentoTotem;
import com.snackbar.pedidos.domain.entities.PagamentoTotem;
import com.snackbar.pedidos.domain.entities.StatusPagamentoTotem;

public record PagamentoTotemDTO(
        String id,
        String pedidoId,
        String correlationId,
        long valorCentavos,
        MeioPagamentoTotem meioPagamento,
        StatusPagamentoTotem status,
        String nsuTef,
        String bandeira,
        String codigoAutorizacao,
        String codigoAdquirente,
        String comprovanteCliente,
        String pixTxid,
        String pixQrCodePayload,
        String pixQrCodeBase64,
        String pixCopiaECola,
        String pixEndToEndId,
        LocalDateTime pixExpiracaoEm,
        String motivo,
        LocalDateTime iniciadoEm,
        LocalDateTime finalizadoEm) {

    public static PagamentoTotemDTO de(PagamentoTotem pagamento) {
        return new PagamentoTotemDTO(
                pagamento.getId(),
                pagamento.getPedidoId(),
                pagamento.getCorrelationId(),
                pagamento.getValorCentavos(),
                pagamento.getMeioPagamento(),
                pagamento.getStatus(),
                pagamento.getNsuTef(),
                pagamento.getBandeira(),
                pagamento.getCodigoAutorizacao(),
                pagamento.getCodigoAdquirente(),
                pagamento.getComprovanteCliente(),
                pagamento.getPixTxid(),
                pagamento.getPixQrCodePayload(),
                pagamento.getPixQrCodeBase64(),
                pagamento.getPixCopiaECola(),
                pagamento.getPixEndToEndId(),
                pagamento.getPixExpiracaoEm(),
                pagamento.getMotivo(),
                pagamento.getIniciadoEm(),
                pagamento.getFinalizadoEm());
    }
}
