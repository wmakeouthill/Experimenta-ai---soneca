package com.snackbar.pedidos.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.snackbar.pedidos.domain.entities.StatusPagamento;

@Repository
public interface PagamentoJpaRepository extends JpaRepository<PagamentoEntity, String> {

    Optional<PagamentoEntity> findByCorrelationId(String correlationId);

    Optional<PagamentoEntity> findByPixTxid(String pixTxid);

    List<PagamentoEntity> findByStatusInAndIniciadoEmBefore(
            Collection<StatusPagamento> status,
            LocalDateTime iniciadoEm);

    List<PagamentoEntity> findByStatus(StatusPagamento status);

    Optional<PagamentoEntity> findFirstByPedidoIdAndStatusOrderByIniciadoEmDesc(
            String pedidoId,
            StatusPagamento status);
}
