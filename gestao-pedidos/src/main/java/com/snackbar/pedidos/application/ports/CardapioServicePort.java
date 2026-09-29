package com.snackbar.pedidos.application.ports;

import com.snackbar.cardapio.application.dto.AdicionalDTO;
import com.snackbar.cardapio.application.dto.ProdutoDTO;
import com.snackbar.kernel.domain.exceptions.ValidationException;

import java.util.List;

public interface CardapioServicePort {
    ProdutoDTO buscarProdutoPorId(String id);

    /**
     * Produto para entrar num pedido: recusa o desativado. Todo canal (balcão, mesa, totem) passa
     * por aqui; a mensagem chega à tela do cliente, por isso cita o nome e não o id.
     */
    default ProdutoDTO buscarProdutoDisponivel(String id) {
        ProdutoDTO produto = buscarProdutoPorId(id);
        if (!produto.isDisponivel()) {
            throw new ValidationException("Produto indisponível no momento: " + produto.getNome());
        }
        return produto;
    }

    AdicionalDTO buscarAdicionalPorId(String id);

    boolean adicionalEstaDisponivel(String id);

    List<AdicionalDTO> listarAdicionaisDisponiveisDoProduto(String produtoId);
}
