package com.snackbar.pedidos.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Dados do cartao digitados pelo cliente. Nao logar nem persistir este objeto.
 */
@Data
public class CartaoInputRequest {

    @NotBlank
    private String numero;

    @NotBlank
    private String nomePortador;

    @NotBlank
    private String validadeMes;

    @NotBlank
    private String validadeAno;

    @NotBlank
    private String cvv;

    @Min(1)
    @Max(12)
    private int parcelas = 1;

    @Override
    public String toString() {
        String ultimos = (numero != null && numero.length() >= 4)
                ? numero.substring(numero.length() - 4) : "****";
        return "CartaoInputRequest[final=" + ultimos + ", parcelas=" + parcelas + "]";
    }
}
