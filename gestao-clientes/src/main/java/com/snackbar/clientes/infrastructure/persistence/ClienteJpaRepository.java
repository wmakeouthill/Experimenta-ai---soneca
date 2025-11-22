package com.snackbar.clientes.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClienteJpaRepository extends JpaRepository<ClienteEntity, String> {
    List<ClienteEntity> findByTelefone(String telefone);
    List<ClienteEntity> findByNomeContainingIgnoreCase(String nome);
}

