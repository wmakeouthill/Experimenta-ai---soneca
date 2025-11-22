package com.snackbar.pedidos.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemPedidoRequest {
    @NotBlank(message = "ID do produto é obrigatório")
    private String produtoId;
    
    @Min(value = 1, message = "Quantidade deve ser maior que zero")
    private int quantidade;
    
    private String observacoes;
}

