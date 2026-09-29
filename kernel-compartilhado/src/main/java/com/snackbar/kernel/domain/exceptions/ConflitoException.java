package com.snackbar.kernel.domain.exceptions;

/** Estado mudou por outra operação concorrente (409), ex.: dois operadores aceitando o mesmo pedido. */
public class ConflitoException extends ValidationException {

    public ConflitoException(String message) {
        super(message);
    }
}
