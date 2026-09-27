package com.snackbar.clientes.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteJpaRepository extends JpaRepository<ClienteEntity, String> {

    @Query(value = "SELECT * FROM clientes WHERE REGEXP_REPLACE(telefone, '[^0-9]', '') = :telefone", nativeQuery = true)
    List<ClienteEntity> findByTelefoneNormalizado(@Param("telefone") String telefone);

    List<ClienteEntity> findByNomeContainingIgnoreCase(String nome);

    Optional<ClienteEntity> findByGoogleId(String googleId);

    Optional<ClienteEntity> findByEmail(String email);

    boolean existsByGoogleId(String googleId);


    boolean existsByEmail(String email);
}
