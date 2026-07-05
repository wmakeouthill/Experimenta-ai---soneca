package com.snackbar.pedidos.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracaoPagamentoJpaRepository extends JpaRepository<ConfiguracaoPagamentoEntity, String> {
}
