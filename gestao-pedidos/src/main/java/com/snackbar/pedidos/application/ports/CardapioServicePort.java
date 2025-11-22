package com.snackbar.pedidos.application.ports;

import com.snackbar.cardapio.application.dto.ProdutoDTO;

public interface CardapioServicePort {
    ProdutoDTO buscarProdutoPorId(String id);
    boolean produtoEstaDisponivel(String id);
}

