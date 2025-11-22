package com.snackbar.pedidos.application.usecases;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.pedidos.application.dto.CriarPedidoRequest;
import com.snackbar.pedidos.application.dto.ItemPedidoRequest;
import com.snackbar.pedidos.application.dto.PedidoDTO;
import com.snackbar.pedidos.application.ports.CardapioServicePort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.ItemPedido;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.services.PedidoValidator;
import com.snackbar.pedidos.domain.valueobjects.NumeroPedido;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CriarPedidoUseCase {
    
    private final PedidoRepositoryPort pedidoRepository;
    private final CardapioServicePort cardapioService;
    private final PedidoValidator pedidoValidator;
    
    public PedidoDTO executar(CriarPedidoRequest request) {
        int ultimoNumero = pedidoRepository.buscarUltimoNumeroPedido();
        NumeroPedido numeroPedido = ultimoNumero == 0 
            ? NumeroPedido.gerarPrimeiro()
            : NumeroPedido.gerarProximo(ultimoNumero);
        
        Pedido pedido = Pedido.criar(
            numeroPedido,
            request.getClienteId(),
            request.getClienteNome(),
            request.getUsuarioId()
        );
        
        for (ItemPedidoRequest itemRequest : request.getItens()) {
            validarProdutoDisponivel(itemRequest.getProdutoId());
            
            var produtoDTO = cardapioService.buscarProdutoPorId(itemRequest.getProdutoId());
            Preco precoUnitario = Preco.of(produtoDTO.getPreco());
            
            ItemPedido item = ItemPedido.criar(
                itemRequest.getProdutoId(),
                produtoDTO.getNome(),
                itemRequest.getQuantidade(),
                precoUnitario,
                itemRequest.getObservacoes()
            );
            
            pedido.adicionarItem(item);
        }
        
        pedido.atualizarObservacoes(request.getObservacoes());
        pedidoValidator.validarCriacao(pedido);
        
        Pedido pedidoSalvo = pedidoRepository.salvar(pedido);
        
        return PedidoDTO.de(pedidoSalvo);
    }
    
    private void validarProdutoDisponivel(String produtoId) {
        if (!cardapioService.produtoEstaDisponivel(produtoId)) {
            throw new com.snackbar.kernel.domain.exceptions.ValidationException(
                "Produto não está disponível: " + produtoId
            );
        }
    }
}

