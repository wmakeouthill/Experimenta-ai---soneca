package com.snackbar.pedidos.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repositório JPA para MovimentacaoCaixaEntity.
 */
@Repository
public interface MovimentacaoCaixaJpaRepository extends JpaRepository<MovimentacaoCaixaEntity, String> {
    
    /**
     * Busca todas as movimentações de uma sessão, ordenadas por data.
     */
    List<MovimentacaoCaixaEntity> findBySessaoIdOrderByDataMovimentacaoDesc(String sessaoId);
    
    /**
     * Busca todas as movimentações de um pedido.
     */
    List<MovimentacaoCaixaEntity> findByPedidoId(String pedidoId);
    
    /**
     * Calcula o saldo total de uma sessão (soma de todas as movimentações).
     */
    @Query("SELECT COALESCE(SUM(m.valor), 0) FROM MovimentacaoCaixaEntity m WHERE m.sessaoId = :sessaoId")
    BigDecimal calcularSaldoSessao(@Param("sessaoId") String sessaoId);
    
    /**
     * Calcula o total de vendas em dinheiro de uma sessão.
     */
    @Query("SELECT COALESCE(SUM(m.valor), 0) FROM MovimentacaoCaixaEntity m " +
           "WHERE m.sessaoId = :sessaoId AND m.tipo = 'VENDA_DINHEIRO'")
    BigDecimal calcularTotalVendasDinheiro(@Param("sessaoId") String sessaoId);
    
    /**
     * Verifica se já existe movimentação de abertura para a sessão.
     */
    boolean existsBySessaoIdAndTipo(String sessaoId, com.snackbar.pedidos.domain.entities.TipoMovimentacaoCaixa tipo);
}

