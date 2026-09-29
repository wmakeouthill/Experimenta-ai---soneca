package com.snackbar.kernel.domain.exceptions;

/** Recurso inexistente (404). Estende ValidationException para não quebrar quem já a captura. */
public class RecursoNaoEncontradoException extends ValidationException {

    public RecursoNaoEncontradoException(String message) {
        super(message);
    }
}
