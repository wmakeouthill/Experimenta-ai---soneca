package com.snackbar.pedidos.application.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para corrigir o troco de um pedido com pagamento em dinheiro.
 * Contém apenas o valor que o cliente realmente pagou em nota.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CorrigirTrocoRequest {

    @NotNull(message = "Valor pago em dinheiro é obrigatório")
    @Positive(message = "Valor pago em dinheiro deve ser maior que zero")
    private BigDecimal valorPagoDinheiro;
}
