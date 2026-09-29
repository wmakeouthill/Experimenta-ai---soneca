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

    /** Mesmo critério de buscarProdutoDisponivel, para os adicionais do item. */
    default AdicionalDTO buscarAdicionalDisponivel(String id) {
        AdicionalDTO adicional = buscarAdicionalPorId(id);
        if (!adicional.isDisponivel()) {
            throw new ValidationException("Adicional indisponível no momento: " + adicional.getNome());
        }
        return adicional;
    }

    List<AdicionalDTO> listarAdicionaisDisponiveisDoProduto(String produtoId);
}
