package com.snackbar.pedidos.application.dto;

import java.time.LocalDateTime;

import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.entities.StatusPagamento;

public record PagamentoDTO(
        String id,
        CanalPagamento canal,
        GatewayPagamento gateway,
        String pedidoId,
        String pedidoPendenteId,
        String contaMesaId,
        String correlationId,
        long valorCentavos,
        MeioPagamentoGateway meioPagamento,
        StatusPagamento status,
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

    public static PagamentoDTO de(Pagamento pagamento) {
        return new PagamentoDTO(
                pagamento.getId(),
                pagamento.getCanal(),
                pagamento.getGateway(),
                pagamento.getPedidoId(),
                pagamento.getPedidoPendenteId(),
                pagamento.getContaMesaId(),
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
