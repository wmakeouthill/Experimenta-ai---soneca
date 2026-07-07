package com.snackbar.pedidos.application.ports;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.snackbar.pedidos.domain.entities.Pagamento;

public interface PagamentoRepositoryPort {

    Pagamento salvar(Pagamento pagamento);

    /** Persiste e força flush para materializar constraints antes de efeitos externos. */
    Pagamento salvarImediato(Pagamento pagamento);

    Optional<Pagamento> buscarPorCorrelationId(String correlationId);

    Optional<Pagamento> buscarPorTxidPix(String txid);

    List<Pagamento> buscarPendentesAnteriores(LocalDateTime dataLimite);

    List<Pagamento> buscarAguardandoPix();

    Optional<Pagamento> buscarAguardandoPixPorPedidoId(String pedidoId);
}
