package com.snackbar.pedidos.domain.valueobjects;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import lombok.Value;

@Value
public class NumeroPedido {
    String numero;
    
    private NumeroPedido(String numero) {
        if (numero == null || numero.trim().isEmpty()) {
            throw new ValidationException("Número do pedido não pode ser nulo ou vazio");
        }
        this.numero = numero.trim();
    }
    
    public static NumeroPedido of(String numero) {
        return new NumeroPedido(numero);
    }
    
    public static NumeroPedido gerarProximo(int ultimoNumero) {
        int proximoNumero = ultimoNumero + 1;
        return new NumeroPedido(String.format("%04d", proximoNumero));
    }
    
    public static NumeroPedido gerarPrimeiro() {
        return gerarProximo(0);
    }
}

