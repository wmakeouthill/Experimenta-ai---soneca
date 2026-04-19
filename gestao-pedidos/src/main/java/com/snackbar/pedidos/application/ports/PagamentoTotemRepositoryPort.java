package com.snackbar.pedidos.application.ports;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.snackbar.pedidos.domain.entities.PagamentoTotem;

public interface PagamentoTotemRepositoryPort {

    PagamentoTotem salvar(PagamentoTotem pagamento);

    Optional<PagamentoTotem> buscarPorCorrelationId(String correlationId);

    Optional<PagamentoTotem> buscarPorTxidPix(String txid);

    List<PagamentoTotem> buscarPendentesAnteriores(LocalDateTime dataLimite);
}
