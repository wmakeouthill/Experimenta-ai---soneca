package com.snackbar.pedidos.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FecharContaComCartaoRequest {

    @NotBlank
    private String mesaToken;

    @NotNull
    @Valid
    private CartaoInputRequest cartao;
}
