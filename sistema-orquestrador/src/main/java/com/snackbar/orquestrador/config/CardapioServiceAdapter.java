package com.snackbar.orquestrador.config;

import com.snackbar.cardapio.application.dto.ProdutoDTO;
import com.snackbar.cardapio.application.usecases.BuscarProdutoPorIdUseCase;
import com.snackbar.pedidos.application.ports.CardapioServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CardapioServiceAdapter implements CardapioServicePort {
    
    private final BuscarProdutoPorIdUseCase buscarProdutoPorIdUseCase;
    
    @Override
    public ProdutoDTO buscarProdutoPorId(String id) {
        return buscarProdutoPorIdUseCase.executar(id);
    }
    
    @Override
    public boolean produtoEstaDisponivel(String id) {
        try {
            ProdutoDTO produto = buscarProdutoPorIdUseCase.executar(id);
            return produto.isDisponivel();
        } catch (Exception e) {
            return false;
        }
    }
}

