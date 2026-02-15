package com.snackbar.cardapio.application.usecases;

import org.springframework.stereotype.Service;

import com.snackbar.cardapio.application.dto.AtualizarCategoriaRequest;
import com.snackbar.cardapio.application.dto.CategoriaDTO;
import com.snackbar.cardapio.application.ports.CategoriaRepositoryPort;
import com.snackbar.cardapio.domain.entities.Categoria;
import com.snackbar.kernel.domain.exceptions.ValidationException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AtualizarCategoriaUseCase {

    private final CategoriaRepositoryPort categoriaRepository;

    @SuppressWarnings("null")
    public CategoriaDTO executar(String id, AtualizarCategoriaRequest request) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("ID da categoria não pode ser nulo ou vazio");
        }

        Categoria categoria = categoriaRepository.buscarPorId(id)
                .orElseThrow(() -> new ValidationException("Categoria não encontrada com ID: " + id));

        if (request.getNome() != null) {
            categoria.atualizarNome(request.getNome());
        }

        if (request.getDescricao() != null) {
            categoria.atualizarDescricao(request.getDescricao());
        }

        if (request.getAtiva() != null) {
            if (request.getAtiva()) {
                categoria.ativar();
            } else {
                categoria.desativar();
            }
        }

        Categoria categoriaAtualizada = categoriaRepository.salvar(categoria);

        return CategoriaDTO.de(categoriaAtualizada);
    }
}
