package com.snackbar.pedidos.infrastructure.persistence.relatorios;

import com.snackbar.pedidos.application.dtos.relatorios.CategoriaVendasResumoDTO;
import com.snackbar.pedidos.application.dtos.relatorios.DistribuicaoClientesDTO;
import com.snackbar.pedidos.application.dtos.relatorios.DistribuicaoHorariaDTO;
import com.snackbar.pedidos.application.dtos.relatorios.DistribuicaoMeioPagamentoDTO;
import com.snackbar.pedidos.application.dtos.relatorios.EvolucaoVendasPontoDTO;
import com.snackbar.pedidos.application.dtos.relatorios.FiltroRelatorioTemporalDTO;
import com.snackbar.pedidos.application.dtos.relatorios.IndicadoresResumoDTO;
import com.snackbar.pedidos.application.dtos.relatorios.PedidosPorHorarioDTO;
import com.snackbar.pedidos.application.dtos.relatorios.ProdutoMaisVendidoDTO;
import com.snackbar.pedidos.application.dtos.relatorios.QuantidadePorCategoriaDTO;
import com.snackbar.pedidos.application.ports.RelatoriosVendasPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RelatoriosVendasRepositoryAdapter implements RelatoriosVendasPort {

    /**
     * Expressão SQL que define a data base para agrupamento de relatórios.
     * 
     * REGRA IMPORTANTE: Quando um pedido está associado a uma sessão, usa-se a data
     * de INÍCIO da sessão
     * (st.data_inicio), não a data do pedido. Isso garante que:
     * - Se uma sessão iniciou no dia 23 e fechou no dia 24, TODOS os pedidos dessa
     * sessão
     * aparecerão apenas no dia 23 (data de início), mesmo que alguns pedidos tenham
     * sido
     * criados após a meia-noite do dia 24.
     * - Apenas pedidos sem sessão associada usam a data do próprio pedido como
     * fallback.
     */
    private static final String DATA_BASE_EXPR = "COALESCE(st.data_inicio, DATE(p.data_pedido))";
    private static final String PARAMETRO_INICIO = "inicio";
    private static final String PARAMETRO_FIM = "fim";
    private static final String SELECT_DATE_CONCAT_YEAR = "SELECT DATE(CONCAT(YEAR(";

    @PersistenceContext
    private final EntityManager entityManager;
    private final RelatorioBucketFactory bucketFactory = new RelatorioBucketFactory();

    @Override
    @Transactional(readOnly = true)
    public List<EvolucaoVendasPontoDTO> obterEvolucao(FiltroRelatorioTemporalDTO filtro) {
        List<RelatorioBucketFactory.RelatorioBucket> buckets;

        if (filtro.granularidade() == com.snackbar.pedidos.application.dtos.relatorios.GranularidadeTempo.ANO) {
            buckets = criarBucketsAnoCompleto();
        } else {
            buckets = bucketFactory.criarBuckets(filtro);
        }

        if (buckets.isEmpty()) {
            return List.of();
        }

        String sql = construirSqlEvolucao(filtro);
        Query query = entityManager.createNativeQuery(sql);

        if (filtro.granularidade() == com.snackbar.pedidos.application.dtos.relatorios.GranularidadeTempo.ANO) {
            query.setParameter(PARAMETRO_INICIO, LocalDate.of(1, 1, 1));
            query.setParameter(PARAMETRO_FIM, LocalDate.now().plusYears(1));
        } else {
            query.setParameter(PARAMETRO_INICIO, filtro.inicio());
            query.setParameter(PARAMETRO_FIM, filtro.fim());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> resultados = query.getResultList();
        for (Object[] registro : resultados) {
            bucketFactory.acumular(
                    buckets,
                    converterData(registro[0]),
                    converterDecimal(registro[1]),
                    converterLong(registro[2]));
        }

        return buckets.stream()
                .map(RelatorioBucketFactory.RelatorioBucket::toDto)
                .toList();
    }

    private String construirSqlEvolucao(FiltroRelatorioTemporalDTO filtro) {
        return switch (filtro.granularidade()) {
            case DIA -> "SELECT " + DATA_BASE_EXPR + " AS data_base, " +
                    "SUM(p.valor_total) AS total_vendas, " +
                    "COUNT(*) AS total_pedidos " +
                    "FROM pedidos p " +
                    "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                    "WHERE " + DATA_BASE_EXPR + " >= :inicio " +
                    "AND " + DATA_BASE_EXPR + " < :fim " +
                    "AND p.status <> 'CANCELADO' " +
                    "GROUP BY data_base " +
                    "ORDER BY data_base";
            case SEMANA -> "SELECT DATE(" + DATA_BASE_EXPR + ") AS data_base, " +
                    "SUM(p.valor_total) AS total_vendas, " +
                    "COUNT(*) AS total_pedidos " +
                    "FROM pedidos p " +
                    "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                    "WHERE " + DATA_BASE_EXPR + " >= :inicio " +
                    "AND " + DATA_BASE_EXPR + " < :fim " +
                    "AND p.status <> 'CANCELADO' " +
                    "GROUP BY YEAR(" + DATA_BASE_EXPR + "), WEEK(" + DATA_BASE_EXPR + ", 1) " +
                    "ORDER BY data_base";
            case MES -> SELECT_DATE_CONCAT_YEAR + DATA_BASE_EXPR + "), '-', MONTH(" + DATA_BASE_EXPR
                    + "), '-01')) AS data_base, " +
                    "SUM(p.valor_total) AS total_vendas, " +
                    "COUNT(*) AS total_pedidos " +
                    "FROM pedidos p " +
                    "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                    "WHERE " + DATA_BASE_EXPR + " >= :inicio " +
                    "AND " + DATA_BASE_EXPR + " < :fim " +
                    "AND p.status <> 'CANCELADO' " +
                    "GROUP BY YEAR(" + DATA_BASE_EXPR + "), MONTH(" + DATA_BASE_EXPR + ") " +
                    "ORDER BY data_base";
            case TRIMESTRE -> SELECT_DATE_CONCAT_YEAR + DATA_BASE_EXPR + "), '-', ((QUARTER(" + DATA_BASE_EXPR
                    + ") - 1) * 3 + 1), '-01')) AS data_base, " +
                    "SUM(p.valor_total) AS total_vendas, " +
                    "COUNT(*) AS total_pedidos " +
                    "FROM pedidos p " +
                    "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                    "WHERE " + DATA_BASE_EXPR + " >= :inicio " +
                    "AND " + DATA_BASE_EXPR + " < :fim " +
                    "AND p.status <> 'CANCELADO' " +
                    "GROUP BY YEAR(" + DATA_BASE_EXPR + "), QUARTER(" + DATA_BASE_EXPR + ") " +
                    "ORDER BY data_base";
            case SEMESTRE -> SELECT_DATE_CONCAT_YEAR + DATA_BASE_EXPR + "), '-', IF(MONTH(" + DATA_BASE_EXPR
                    + ") <= 6, 1, 7), '-01')) AS data_base, " +
                    "SUM(p.valor_total) AS total_vendas, " +
                    "COUNT(*) AS total_pedidos " +
                    "FROM pedidos p " +
                    "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                    "WHERE " + DATA_BASE_EXPR + " >= :inicio " +
                    "AND " + DATA_BASE_EXPR + " < :fim " +
                    "AND p.status <> 'CANCELADO' " +
                    "GROUP BY YEAR(" + DATA_BASE_EXPR + "), IF(MONTH(" + DATA_BASE_EXPR + ") <= 6, 1, 2) " +
                    "ORDER BY data_base";
            case ANO -> SELECT_DATE_CONCAT_YEAR + DATA_BASE_EXPR + "), '-01-01')) AS data_base, " +
                    "SUM(p.valor_total) AS total_vendas, " +
                    "COUNT(*) AS total_pedidos " +
                    "FROM pedidos p " +
                    "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                    "WHERE p.status <> 'CANCELADO' " +
                    "GROUP BY YEAR(" + DATA_BASE_EXPR + ") " +
                    "ORDER BY data_base";
        };
    }

    private List<RelatorioBucketFactory.RelatorioBucket> criarBucketsAnoCompleto() {
        String sql = "SELECT DISTINCT YEAR(" + DATA_BASE_EXPR + ") AS ano " +
                "FROM pedidos p " +
                "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                "WHERE p.status <> 'CANCELADO' " +
                "ORDER BY ano";
        Query query = entityManager.createNativeQuery(sql);
        @SuppressWarnings("unchecked")
        List<Object> anos = query.getResultList();

        List<RelatorioBucketFactory.RelatorioBucket> buckets = new java.util.ArrayList<>();
        for (Object anoObj : anos) {
            int ano = ((Number) anoObj).intValue();
            buckets.add(bucketFactory.criarBucketAno(ano));
        }
        return buckets;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaVendasResumoDTO> obterCategorias(FiltroRelatorioTemporalDTO filtro) {
        String sql = "SELECT COALESCE(prod.categoria, 'Sem categoria') AS categoria_nome, " +
                "SUM(item.preco_unitario * item.quantidade) AS valor_total, " +
                "COUNT(DISTINCT p.id) AS total_pedidos " +
                "FROM pedidos p " +
                "JOIN itens_pedido item ON item.pedido_id = p.id " +
                "LEFT JOIN produtos prod ON prod.id = item.produto_id " +
                "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                "WHERE " + DATA_BASE_EXPR + " >= :inicio " +
                "AND " + DATA_BASE_EXPR + " < :fim " +
                "AND p.status <> 'CANCELADO' " +
                "GROUP BY categoria_nome " +
                "ORDER BY valor_total DESC";
        Query query = entityManager.createNativeQuery(sql);
        configurarIntervalo(query, filtro);
        return RelatorioResultMapper.categorias(query.getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuantidadePorCategoriaDTO> obterQuantidadePorCategoria(FiltroRelatorioTemporalDTO filtro) {
        String sql = "SELECT COALESCE(prod.categoria, 'Sem categoria') AS categoria_id, " +
                "COALESCE(prod.categoria, 'Sem categoria') AS categoria_nome, " +
                "SUM(item.quantidade) AS quantidade_vendida " +
                "FROM pedidos p " +
                "JOIN itens_pedido item ON item.pedido_id = p.id " +
                "LEFT JOIN produtos prod ON prod.id = item.produto_id " +
                "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                "WHERE " + DATA_BASE_EXPR + " >= :inicio " +
                "AND " + DATA_BASE_EXPR + " < :fim " +
                "AND p.status <> 'CANCELADO' " +
                "GROUP BY categoria_id, categoria_nome " +
                "ORDER BY quantidade_vendida DESC";
        Query query = entityManager.createNativeQuery(sql);
        configurarIntervalo(query, filtro);
        return RelatorioResultMapper.quantidadePorCategoria(query.getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProdutoMaisVendidoDTO> obterTopProdutos(FiltroRelatorioTemporalDTO filtro, int limite) {
        String sql = "SELECT item.produto_id, " +
                "item.produto_nome, " +
                "SUM(item.quantidade) AS quantidade, " +
                "SUM(item.preco_unitario * item.quantidade) AS valor_total " +
                "FROM pedidos p " +
                "JOIN itens_pedido item ON item.pedido_id = p.id " +
                "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                "WHERE " + DATA_BASE_EXPR + " >= :inicio " +
                "AND " + DATA_BASE_EXPR + " < :fim " +
                "AND p.status <> 'CANCELADO' " +
                "GROUP BY item.produto_id, item.produto_nome " +
                "ORDER BY quantidade DESC, valor_total DESC";
        Query query = entityManager.createNativeQuery(sql);
        configurarIntervalo(query, filtro);
        query.setMaxResults(Math.max(limite, 1));
        return RelatorioResultMapper.produtos(query.getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DistribuicaoHorariaDTO> obterDistribuicaoHoraria(FiltroRelatorioTemporalDTO filtro) {
        String sql = "SELECT LPAD(HOUR(p.data_pedido), 2, '0') AS hora, " +
                "SUM(p.valor_total) AS valor_total, " +
                "COUNT(*) AS total_pedidos " +
                "FROM pedidos p " +
                "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                "WHERE " + DATA_BASE_EXPR + " >= :inicio " +
                "AND " + DATA_BASE_EXPR + " < :fim " +
                "AND p.status <> 'CANCELADO' " +
                "GROUP BY hora " +
                "ORDER BY MIN(p.data_pedido)";
        Query query = entityManager.createNativeQuery(sql);
        configurarIntervalo(query, filtro);
        return RelatorioResultMapper.horarios(query.getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedidosPorHorarioDTO> obterPedidosPorHorario(FiltroRelatorioTemporalDTO filtro) {
        String sql = "SELECT LPAD(HOUR(p.data_pedido), 2, '0') AS hora, " +
                "COUNT(*) AS quantidade_pedidos, " +
                "SUM(p.valor_total) AS valor_total " +
                "FROM pedidos p " +
                "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                "WHERE " + DATA_BASE_EXPR + " >= :inicio " +
                "AND " + DATA_BASE_EXPR + " < :fim " +
                "AND p.status <> 'CANCELADO' " +
                "GROUP BY hora " +
                "ORDER BY MIN(p.data_pedido)";
        Query query = entityManager.createNativeQuery(sql);
        configurarIntervalo(query, filtro);
        return RelatorioResultMapper.pedidosPorHorario(query.getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DistribuicaoClientesDTO> obterClientes(FiltroRelatorioTemporalDTO filtro, int limite) {
        String sql = "SELECT p.cliente_id, " +
                "p.cliente_nome, " +
                "SUM(p.valor_total) AS valor_total, " +
                "COUNT(*) AS total_pedidos " +
                "FROM pedidos p " +
                "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                "WHERE " + DATA_BASE_EXPR + " >= :inicio " +
                "AND " + DATA_BASE_EXPR + " < :fim " +
                "AND p.status <> 'CANCELADO' " +
                "GROUP BY p.cliente_id, p.cliente_nome " +
                "ORDER BY valor_total DESC";
        Query query = entityManager.createNativeQuery(sql);
        configurarIntervalo(query, filtro);
        query.setMaxResults(Math.max(limite, 1));
        return RelatorioResultMapper.clientes(query.getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DistribuicaoMeioPagamentoDTO> obterMeiosPagamento(FiltroRelatorioTemporalDTO filtro) {
        String sql = "SELECT pagamento.meio_pagamento, " +
                "SUM(pagamento.valor) AS valor_total, " +
                "COUNT(DISTINCT p.id) AS pedidos " +
                "FROM pedidos p " +
                "JOIN meios_pagamento_pedido pagamento ON pagamento.pedido_id = p.id " +
                "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                "WHERE " + DATA_BASE_EXPR + " >= :inicio " +
                "AND " + DATA_BASE_EXPR + " < :fim " +
                "AND p.status <> 'CANCELADO' " +
                "GROUP BY pagamento.meio_pagamento " +
                "ORDER BY valor_total DESC";
        Query query = entityManager.createNativeQuery(sql);
        configurarIntervalo(query, filtro);
        return RelatorioResultMapper.meiosPagamento(query.getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresResumoDTO obterIndicadores(FiltroRelatorioTemporalDTO filtro) {
        TotaisPeriodo atual = buscarTotais(filtro.inicio(), filtro.fim());
        TotaisPeriodo anterior = buscarTotais(filtro.inicioPeriodoAnterior(), filtro.fimPeriodoAnterior());
        double ticket = atual.totalPedidos() == 0 ? 0 : atual.totalVendas().doubleValue() / atual.totalPedidos();
        double crescimento = calcularCrescimento(atual.totalVendas(), anterior.totalVendas());
        return new IndicadoresResumoDTO(
                atual.totalVendas().doubleValue(),
                atual.totalPedidos(),
                ticket,
                crescimento);
    }

    private TotaisPeriodo buscarTotais(LocalDate inicio, LocalDate fim) {
        String sql = "SELECT COALESCE(SUM(p.valor_total), 0) AS total_vendas, " +
                "COUNT(*) AS total_pedidos " +
                "FROM pedidos p " +
                "LEFT JOIN sessoes_trabalho st ON st.id = p.sessao_id " +
                "WHERE " + DATA_BASE_EXPR + " >= :inicio " +
                "AND " + DATA_BASE_EXPR + " < :fim " +
                "AND p.status <> 'CANCELADO'";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(PARAMETRO_INICIO, inicio);
        query.setParameter(PARAMETRO_FIM, fim);
        Object[] resultado = (Object[]) query.getSingleResult();
        BigDecimal total = converterDecimal(resultado[0]);
        long pedidos = converterLong(resultado[1]);
        return new TotaisPeriodo(total, pedidos);
    }

    private double calcularCrescimento(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.compareTo(BigDecimal.ZERO) == 0) {
            return atual.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        }
        BigDecimal diferenca = atual.subtract(anterior);
        return diferenca.divide(anterior, 4, RoundingMode.HALF_UP).doubleValue() * 100;
    }

    private void configurarIntervalo(Query query, FiltroRelatorioTemporalDTO filtro) {
        query.setParameter(PARAMETRO_INICIO, filtro.inicio());
        query.setParameter(PARAMETRO_FIM, filtro.fim());
    }

    private LocalDate converterData(Object valor) {
        if (valor instanceof LocalDate localDate) {
            return localDate;
        }
        if (valor instanceof Date date) {
            return date.toLocalDate();
        }
        throw new IllegalArgumentException("Tipo de data não suportado: " + valor);
    }

    private BigDecimal converterDecimal(Object valor) {
        if (valor instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(valor.toString());
    }

    private long converterLong(Object valor) {
        if (valor == null) {
            return 0L;
        }
        return ((Number) valor).longValue();
    }

    private record TotaisPeriodo(BigDecimal totalVendas, long totalPedidos) {
    }
}
