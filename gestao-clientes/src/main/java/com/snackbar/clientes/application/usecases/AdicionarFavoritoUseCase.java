package com.snackbar.clientes.application.usecases;

import com.snackbar.clientes.application.dto.AdicionarFavoritoRequest;
import com.snackbar.clientes.application.dto.ClienteFavoritoDTO;
import com.snackbar.clientes.application.ports.ClienteFavoritoRepositoryPort;
import com.snackbar.clientes.application.ports.ClienteRepositoryPort;
import com.snackbar.clientes.domain.entities.ClienteFavorito;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdicionarFavoritoUseCase {

    private final ClienteFavoritoRepositoryPort favoritoRepository;
    private final ClienteRepositoryPort clienteRepository;

    public ClienteFavoritoDTO executar(String clienteId, AdicionarFavoritoRequest request) {
        // Validar se cliente existe
        clienteRepository.buscarPorId(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado: " + clienteId));

        // Verificar se já é favorito
        if (favoritoRepository.existe(clienteId, request.getProdutoId())) {
            throw new IllegalStateException("Produto já está nos favoritos");
        }

        ClienteFavorito favorito = ClienteFavorito.criar(clienteId, request.getProdutoId());
        ClienteFavorito salvo = favoritoRepository.salvar(favorito);

        return ClienteFavoritoDTO.de(salvo);
    }
}
