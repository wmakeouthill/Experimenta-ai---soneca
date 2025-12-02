package com.snackbar.pedidos.application.ports;

import com.snackbar.pedidos.domain.entities.MovimentacaoCaixa;
import com.snackbar.pedidos.domain.entities.TipoMovimentacaoCaixa;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Port para o repositório de movimentações de caixa.
 */
public interface MovimentacaoCaixaRepositoryPort {
    
    MovimentacaoCaixa salvar(MovimentacaoCaixa movimentacao);
    
    Optional<MovimentacaoCaixa> buscarPorId(String id);
    
    List<MovimentacaoCaixa> buscarPorSessaoId(String sessaoId);
    
    List<MovimentacaoCaixa> buscarPorPedidoId(String pedidoId);
    
    BigDecimal calcularSaldoSessao(String sessaoId);
    
    BigDecimal calcularTotalVendasDinheiro(String sessaoId);
    
    boolean existeMovimentacaoTipo(String sessaoId, TipoMovimentacaoCaixa tipo);
}

