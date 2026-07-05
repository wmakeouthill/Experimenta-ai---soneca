package com.snackbar.pedidos.infrastructure.mappers;

import org.springframework.stereotype.Component;

import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.infrastructure.persistence.PagamentoEntity;

@Component
public class PagamentoMapper {

    public PagamentoEntity paraEntity(Pagamento pagamento) {
        PagamentoEntity.PagamentoEntityBuilder builder = PagamentoEntity.builder()
                .id(pagamento.getId())
                .canal(pagamento.getCanal())
                .gateway(pagamento.getGateway())
                .pedidoId(pagamento.getPedidoId())
                .pedidoPendenteId(pagamento.getPedidoPendenteId())
                .contaMesaId(pagamento.getContaMesaId())
                .correlationId(pagamento.getCorrelationId())
                .gatewayPaymentId(pagamento.getGatewayPaymentId())
                .valorCentavos(pagamento.getValorCentavos())
                .meioPagamento(pagamento.getMeioPagamento())
                .status(pagamento.getStatus())
                .nsuTef(pagamento.getNsuTef())
                .bandeira(pagamento.getBandeira())
                .codigoAutorizacao(pagamento.getCodigoAutorizacao())
                .codigoAdquirente(pagamento.getCodigoAdquirente())
                .comprovanteCliente(pagamento.getComprovanteCliente())
                .pixTxid(pagamento.getPixTxid())
                .pixQrCodePayload(pagamento.getPixQrCodePayload())
                .pixQrCodeBase64(pagamento.getPixQrCodeBase64())
                .pixCopiaECola(pagamento.getPixCopiaECola())
                .pixEndToEndId(pagamento.getPixEndToEndId())
                .pixExpiracaoEm(pagamento.getPixExpiracaoEm())
                .motivo(pagamento.getMotivo())
                .iniciadoEm(pagamento.getIniciadoEm())
                .finalizadoEm(pagamento.getFinalizadoEm())
                .createdAt(pagamento.getCreatedAt())
                .updatedAt(pagamento.getUpdatedAt());

        if (pagamento.getVersion() != null) {
            builder.version(pagamento.getVersion());
            builder.novo(false);
        }

        return builder.build();
    }

    public Pagamento paraDomain(PagamentoEntity entity) {
        return Pagamento.restaurar(
                entity.getId(),
                entity.getCanal(),
                entity.getGateway(),
                entity.getPedidoId(),
                entity.getPedidoPendenteId(),
                entity.getContaMesaId(),
                entity.getCorrelationId(),
                entity.getGatewayPaymentId(),
                entity.getValorCentavos(),
                entity.getMeioPagamento(),
                entity.getStatus(),
                entity.getNsuTef(),
                entity.getBandeira(),
                entity.getCodigoAutorizacao(),
                entity.getCodigoAdquirente(),
                entity.getComprovanteCliente(),
                entity.getPixTxid(),
                entity.getPixQrCodePayload(),
                entity.getPixQrCodeBase64(),
                entity.getPixCopiaECola(),
                entity.getPixEndToEndId(),
                entity.getPixExpiracaoEm(),
                entity.getMotivo(),
                entity.getIniciadoEm(),
                entity.getFinalizadoEm(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }
}
