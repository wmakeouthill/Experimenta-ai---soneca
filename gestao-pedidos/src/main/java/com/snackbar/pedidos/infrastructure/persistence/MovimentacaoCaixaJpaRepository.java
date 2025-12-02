package com.snackbar.pedidos.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repositório JPA para MovimentacaoCaixaEntity.
 * Armazena apenas sangrias e suprimentos.
 */
@Repository
public interface MovimentacaoCaixaJpaRepository extends JpaRepository<MovimentacaoCaixaEntity, String> {
    
    /**
     * Busca todas as movimentações de uma sessão, ordenadas por data.
     */
    List<MovimentacaoCaixaEntity> findBySessaoIdOrderByDataMovimentacaoDesc(String sessaoId);
    
    /**
     * Calcula o saldo total de uma sessão (soma de todas as movimentações - sangrias e suprimentos).
     */
    @Query("SELECT COALESCE(SUM(m.valor), 0) FROM MovimentacaoCaixaEntity m WHERE m.sessaoId = :sessaoId")
    BigDecimal calcularSaldoSessao(@Param("sessaoId") String sessaoId);
    
    /**
     * Verifica se já existe movimentação de determinado tipo para a sessão.
     */
    boolean existsBySessaoIdAndTipo(String sessaoId, com.snackbar.pedidos.domain.entities.TipoMovimentacaoCaixa tipo);
}

