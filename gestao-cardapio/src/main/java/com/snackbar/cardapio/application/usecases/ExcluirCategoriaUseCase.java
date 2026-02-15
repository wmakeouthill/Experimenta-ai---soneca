package com.snackbar.cardapio.application.usecases;

import org.springframework.stereotype.Service;

import com.snackbar.cardapio.application.ports.CategoriaRepositoryPort;
import com.snackbar.kernel.domain.exceptions.ValidationException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExcluirCategoriaUseCase {

    private final CategoriaRepositoryPort categoriaRepository;

    public void executar(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("ID da categoria não pode ser nulo ou vazio");
        }

        if (!categoriaRepository.existePorId(id)) {
            throw new ValidationException("Categoria não encontrada com ID: " + id);
        }

        categoriaRepository.excluir(id);
    }
}
