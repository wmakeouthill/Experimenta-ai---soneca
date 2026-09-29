package com.snackbar.orquestrador.config;

import com.snackbar.cardapio.application.dto.AdicionalDTO;
import com.snackbar.cardapio.application.dto.ProdutoDTO;
import com.snackbar.cardapio.application.usecases.BuscarAdicionalPorIdUseCase;
import com.snackbar.cardapio.application.usecases.BuscarProdutoPorIdUseCase;
import com.snackbar.cardapio.application.usecases.GerenciarAdicionaisProdutoUseCase;
import com.snackbar.pedidos.application.ports.CardapioServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CardapioServiceAdapter implements CardapioServicePort {

    private final BuscarProdutoPorIdUseCase buscarProdutoPorIdUseCase;
    private final BuscarAdicionalPorIdUseCase buscarAdicionalPorIdUseCase;
    private final GerenciarAdicionaisProdutoUseCase gerenciarAdicionaisProdutoUseCase;

    @Override
    public ProdutoDTO buscarProdutoPorId(String id) {
        return buscarProdutoPorIdUseCase.executar(id);
    }

    @Override
    public AdicionalDTO buscarAdicionalPorId(String id) {
        return buscarAdicionalPorIdUseCase.executar(id);
    }

    @Override
    public List<AdicionalDTO> listarAdicionaisDisponiveisDoProduto(String produtoId) {
        return gerenciarAdicionaisProdutoUseCase.buscarAdicionaisDoProduto(produtoId).stream()
                .filter(AdicionalDTO::isDisponivel)
                .toList();
    }
}
