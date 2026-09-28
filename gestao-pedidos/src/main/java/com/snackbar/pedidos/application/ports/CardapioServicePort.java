package com.snackbar.pedidos.application.ports;

import com.snackbar.cardapio.application.dto.AdicionalDTO;
import com.snackbar.cardapio.application.dto.ProdutoDTO;

import java.util.List;

public interface CardapioServicePort {
    ProdutoDTO buscarProdutoPorId(String id);

    boolean produtoEstaDisponivel(String id);

    AdicionalDTO buscarAdicionalPorId(String id);

    boolean adicionalEstaDisponivel(String id);

    List<AdicionalDTO> listarAdicionaisDisponiveisDoProduto(String produtoId);
}
