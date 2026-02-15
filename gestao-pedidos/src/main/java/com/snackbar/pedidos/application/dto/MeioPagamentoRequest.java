package com.snackbar.pedidos.application.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeioPagamentoRequest {
    @NotNull(message = "Meio de pagamento é obrigatório")
    private com.snackbar.pedidos.domain.entities.MeioPagamento meioPagamento;

    @NotNull(message = "Valor é obrigatório")
    @Positive(message = "Valor deve ser maior que zero")
    private BigDecimal valor;

    /**
     * Valor pago em nota pelo cliente (apenas para DINHEIRO).
     * Se informado, o troco é calculado automaticamente.
     */
    @Positive(message = "Valor pago em dinheiro deve ser maior que zero")
    private BigDecimal valorPagoDinheiro;
}
