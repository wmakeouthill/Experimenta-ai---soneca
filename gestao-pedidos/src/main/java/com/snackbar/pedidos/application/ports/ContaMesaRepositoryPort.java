package com.snackbar.pedidos.application.ports;

import java.util.Optional;

import com.snackbar.pedidos.domain.entities.ContaMesa;

public interface ContaMesaRepositoryPort {

    ContaMesa salvar(ContaMesa conta);

    Optional<ContaMesa> buscarPorId(String id);

    /**
     * Retorna a conta ABERTA de um cliente numa mesa, se existir (idempotencia
     * do fechamento de conta).
     */
    Optional<ContaMesa> buscarAbertaPorMesaECliente(String mesaId, String clienteId);
}
