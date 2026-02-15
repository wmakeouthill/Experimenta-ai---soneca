package com.snackbar.pedidos.application.dto;

import java.math.BigDecimal;

import com.snackbar.pedidos.domain.entities.MeioPagamentoPedido;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeioPagamentoDTO {
    private com.snackbar.pedidos.domain.entities.MeioPagamento meioPagamento;
    private BigDecimal valor;
    private BigDecimal valorPagoDinheiro;
    private BigDecimal troco;

    public static MeioPagamentoDTO de(MeioPagamentoPedido meioPagamentoPedido) {
        MeioPagamentoDTOBuilder builder = MeioPagamentoDTO.builder()
                .meioPagamento(meioPagamentoPedido.getMeioPagamento())
                .valor(meioPagamentoPedido.getValor().getAmount());

        if (meioPagamentoPedido.getValorPagoDinheiro() != null) {
            builder.valorPagoDinheiro(meioPagamentoPedido.getValorPagoDinheiro().getAmount());
        }
        if (meioPagamentoPedido.getTroco() != null) {
            builder.troco(meioPagamentoPedido.getTroco().getAmount());
        }

        return builder.build();
    }
}
