package com.snackbar.pedidos.application.ports;

import com.snackbar.pedidos.application.dtos.relatorios.CategoriaVendasResumoDTO;
import com.snackbar.pedidos.application.dtos.relatorios.DistribuicaoClientesDTO;
import com.snackbar.pedidos.application.dtos.relatorios.DistribuicaoHorariaDTO;
import com.snackbar.pedidos.application.dtos.relatorios.DistribuicaoMeioPagamentoDTO;
import com.snackbar.pedidos.application.dtos.relatorios.EvolucaoVendasPontoDTO;
import com.snackbar.pedidos.application.dtos.relatorios.FiltroRelatorioTemporalDTO;
import com.snackbar.pedidos.application.dtos.relatorios.IndicadoresResumoDTO;
import com.snackbar.pedidos.application.dtos.relatorios.ProdutoMaisVendidoDTO;

import java.util.List;

public interface RelatoriosVendasPort {
    List<EvolucaoVendasPontoDTO> obterEvolucao(FiltroRelatorioTemporalDTO filtro);

    List<CategoriaVendasResumoDTO> obterCategorias(FiltroRelatorioTemporalDTO filtro);

    List<ProdutoMaisVendidoDTO> obterTopProdutos(FiltroRelatorioTemporalDTO filtro, int limite);

    List<DistribuicaoHorariaDTO> obterDistribuicaoHoraria(FiltroRelatorioTemporalDTO filtro);

    List<DistribuicaoClientesDTO> obterClientes(FiltroRelatorioTemporalDTO filtro, int limite);

    List<DistribuicaoMeioPagamentoDTO> obterMeiosPagamento(FiltroRelatorioTemporalDTO filtro);

    IndicadoresResumoDTO obterIndicadores(FiltroRelatorioTemporalDTO filtro);
}

