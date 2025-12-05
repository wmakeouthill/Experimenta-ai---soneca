package com.snackbar.clientes.application.ports;

import com.snackbar.clientes.domain.entities.ClienteAvaliacao;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface ClienteAvaliacaoRepositoryPort {

    ClienteAvaliacao salvar(@NonNull ClienteAvaliacao avaliacao);

    void remover(@NonNull String id);

    Optional<ClienteAvaliacao> buscar(@NonNull String clienteId, @NonNull String produtoId);

    Optional<ClienteAvaliacao> buscarPorId(@NonNull String id);

    List<ClienteAvaliacao> buscarPorCliente(@NonNull String clienteId);

    List<ClienteAvaliacao> buscarPorProduto(@NonNull String produtoId);

    Double calcularMediaPorProduto(@NonNull String produtoId);

    int contarAvaliacoesPorProduto(@NonNull String produtoId);
}
