package com.snackbar.pedidos.application.dto;

import com.snackbar.pedidos.domain.entities.Piso;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Painel (TV) para o qual o operador move um pedido já criado.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AtualizarPisoPedidoRequest {

    @NotNull(message = "Piso é obrigatório")
    private Piso piso;
}
