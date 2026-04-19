package com.snackbar.pedidos.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.snackbar.pedidos.domain.entities.StatusPagamentoTotem;

@Repository
public interface PagamentoTotemJpaRepository extends JpaRepository<PagamentoTotemEntity, String> {

    Optional<PagamentoTotemEntity> findByCorrelationId(String correlationId);

    Optional<PagamentoTotemEntity> findByPixTxid(String pixTxid);

    List<PagamentoTotemEntity> findByStatusInAndIniciadoEmBefore(
            Collection<StatusPagamentoTotem> status,
            LocalDateTime iniciadoEm);
}
