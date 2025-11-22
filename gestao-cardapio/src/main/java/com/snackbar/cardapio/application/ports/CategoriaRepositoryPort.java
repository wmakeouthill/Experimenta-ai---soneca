package com.snackbar.cardapio.application.ports;

import com.snackbar.cardapio.domain.entities.Categoria;
import java.util.List;
import java.util.Optional;

public interface CategoriaRepositoryPort {
    Categoria salvar(Categoria categoria);
    Optional<Categoria> buscarPorId(String id);
    List<Categoria> buscarTodas();
    List<Categoria> buscarAtivas();
    void excluir(String id);
    boolean existePorId(String id);
}

