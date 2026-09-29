package com.snackbar.pedidos.infrastructure.persistence;

import com.snackbar.pedidos.domain.entities.StatusSessao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessaoTrabalhoJpaRepository extends JpaRepository<SessaoTrabalhoEntity, String> {
    Optional<SessaoTrabalhoEntity> findFirstByStatusInOrderByDataInicioCompletaDesc(List<StatusSessao> statuses);

    /** Só o id: não deixa a entidade no contexto de persistência antes da leitura travada. */
    @Query("SELECT s.id FROM SessaoTrabalhoEntity s WHERE s.status IN :statuses ORDER BY s.dataInicioCompleta DESC")
    List<String> findIdsByStatusIn(@Param("statuses") List<StatusSessao> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SessaoTrabalhoEntity s WHERE s.id = :id AND s.status IN :statuses")
    Optional<SessaoTrabalhoEntity> findByIdAndStatusInComLock(
            @Param("id") String id, @Param("statuses") List<StatusSessao> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SessaoTrabalhoEntity s WHERE s.id = :id")
    Optional<SessaoTrabalhoEntity> findByIdComLock(@Param("id") String id);
    
    Optional<SessaoTrabalhoEntity> findFirstByStatusOrderByDataInicioCompletaDesc(StatusSessao status);
    
    List<SessaoTrabalhoEntity> findByDataInicioOrderByNumeroSessaoDesc(LocalDate dataInicio);
    
    Optional<SessaoTrabalhoEntity> findFirstByDataInicioOrderByNumeroSessaoDesc(LocalDate dataInicio);
    
    List<SessaoTrabalhoEntity> findAllByOrderByDataInicioCompletaDesc();
    
    List<SessaoTrabalhoEntity> findByStatusOrderByDataInicioCompletaDesc(StatusSessao status);
    
    Optional<SessaoTrabalhoEntity> findFirstByDataInicioCompletaBeforeAndStatusOrderByDataInicioCompletaDesc(
            LocalDateTime dataInicioCompleta, StatusSessao status);
}

