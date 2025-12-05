package com.snackbar.clientes.infrastructure.persistence;

import com.snackbar.clientes.application.ports.ClienteFavoritoRepositoryPort;
import com.snackbar.clientes.domain.entities.ClienteFavorito;
import com.snackbar.clientes.infrastructure.mappers.ClienteFavoritoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClienteFavoritoRepositoryAdapter implements ClienteFavoritoRepositoryPort {

    private final ClienteFavoritoJpaRepository jpaRepository;
    private final ClienteFavoritoMapper mapper;

    @Override
    @SuppressWarnings("null")
    public ClienteFavorito salvar(@NonNull ClienteFavorito favorito) {
        ClienteFavoritoEntity entity = mapper.paraEntity(favorito);
        ClienteFavoritoEntity salvo = jpaRepository.save(entity);
        return mapper.paraDomain(salvo);
    }

    @Override
    public void remover(@NonNull String clienteId, @NonNull String produtoId) {
        jpaRepository.deleteByClienteIdAndProdutoId(clienteId, produtoId);
    }

    @Override
    public Optional<ClienteFavorito> buscar(@NonNull String clienteId, @NonNull String produtoId) {
        return jpaRepository.findByClienteIdAndProdutoId(clienteId, produtoId)
                .map(mapper::paraDomain);
    }

    @Override
    public List<ClienteFavorito> buscarPorCliente(@NonNull String clienteId) {
        return jpaRepository.findByClienteIdOrderByCreatedAtDesc(clienteId).stream()
                .map(mapper::paraDomain)
                .toList();
    }

    @Override
    public boolean existe(@NonNull String clienteId, @NonNull String produtoId) {
        return jpaRepository.existsByClienteIdAndProdutoId(clienteId, produtoId);
    }

    @Override
    public int contarPorCliente(@NonNull String clienteId) {
        return jpaRepository.countByClienteId(clienteId);
    }
}
