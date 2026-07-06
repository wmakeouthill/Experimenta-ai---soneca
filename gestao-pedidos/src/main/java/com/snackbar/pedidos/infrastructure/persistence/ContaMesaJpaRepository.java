package com.snackbar.pedidos.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.snackbar.pedidos.domain.entities.StatusContaMesa;

public interface ContaMesaJpaRepository extends JpaRepository<ContaMesaEntity, String> {

    @Query("SELECT c FROM ContaMesaEntity c " +
            "WHERE c.mesaId = :mesaId AND c.clienteId = :clienteId AND c.status = :status")
    Optional<ContaMesaEntity> findByMesaClienteStatus(
            @Param("mesaId") String mesaId,
            @Param("clienteId") String clienteId,
            @Param("status") StatusContaMesa status);
}
