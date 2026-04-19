package com.snackbar.pedidos.domain.valueobjects;

import com.snackbar.kernel.domain.exceptions.ValidationException;

public record DadosTef(
        String nsu,
        String bandeira,
        String codigoAutorizacao,
        String codigoAdquirente,
        String comprovanteCliente) {

    public DadosTef {
        if (nsu == null || nsu.isBlank()) {
            throw new ValidationException("NSU do TEF e obrigatorio");
        }
    }
}
