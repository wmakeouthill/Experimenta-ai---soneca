package com.snackbar.cardapio.application.ports;

import com.snackbar.cardapio.domain.entities.Produto;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepositoryPort {
    Produto salvar(Produto produto);
    Optional<Produto> buscarPorId(String id);
    List<Produto> buscarTodos();
    List<Produto> buscarPorCategoria(String categoria);
    List<Produto> buscarDisponiveis();
    void excluir(String id);
    boolean existePorId(String id);
}

