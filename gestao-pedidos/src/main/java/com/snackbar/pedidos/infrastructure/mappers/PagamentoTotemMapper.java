package com.snackbar.pedidos.infrastructure.mappers;

import org.springframework.stereotype.Component;

import com.snackbar.pedidos.domain.entities.PagamentoTotem;
import com.snackbar.pedidos.infrastructure.persistence.PagamentoTotemEntity;

@Component
public class PagamentoTotemMapper {

    public PagamentoTotemEntity paraEntity(PagamentoTotem pagamento) {
        PagamentoTotemEntity.PagamentoTotemEntityBuilder builder = PagamentoTotemEntity.builder()
                .id(pagamento.getId())
                .pedidoId(pagamento.getPedidoId())
                .correlationId(pagamento.getCorrelationId())
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

    public PagamentoTotem paraDomain(PagamentoTotemEntity entity) {
        return PagamentoTotem.restaurar(
                entity.getId(),
                entity.getPedidoId(),
                entity.getCorrelationId(),
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
