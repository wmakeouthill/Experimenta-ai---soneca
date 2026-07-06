package com.snackbar.pedidos.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PagarPedidoMesaComCartaoRequest {

    @NotNull
    @Valid
    private CriarPedidoMesaRequest pedido;

    @NotNull
    @Valid
    private CartaoInputRequest cartao;
}
