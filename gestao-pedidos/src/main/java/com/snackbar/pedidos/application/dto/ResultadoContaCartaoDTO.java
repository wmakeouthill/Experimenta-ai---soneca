package com.snackbar.pedidos.application.dto;

public record ResultadoContaCartaoDTO(boolean aprovado, String motivo, ContaMesaDTO conta) {
}
