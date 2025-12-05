package com.snackbar.clientes.application.usecases;

import com.snackbar.clientes.application.ports.ClienteFavoritoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RemoverFavoritoUseCase {

    private final ClienteFavoritoRepositoryPort favoritoRepository;

    public void executar(String clienteId, String produtoId) {
        if (!favoritoRepository.existe(clienteId, produtoId)) {
            throw new IllegalArgumentException("Favorito não encontrado");
        }

        favoritoRepository.remover(clienteId, produtoId);
    }
}
