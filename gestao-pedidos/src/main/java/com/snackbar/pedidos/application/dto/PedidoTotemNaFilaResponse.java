package com.snackbar.pedidos.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response quando um pedido de totem é enviado para a fila de aceitação.
 * O pedido só vira pedido real após um funcionário aceitar no painel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoTotemNaFilaResponse {

    /** ID do pedido pendente na fila (para rastreio). */
    private String id;

    /** Status fixo indicando que está aguardando aceitação. */
    @Builder.Default
    private String status = "NA_FILA";

    /** Mensagem para exibir no totem (ex: "Pedido enviado! Aguarde a confirmação do atendente."). */
    private String mensagem;
}
