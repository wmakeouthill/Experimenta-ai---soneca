package com.snackbar.pedidos.infrastructure.gateway;

import com.snackbar.cardapio.application.dto.ProdutoDTO;
import com.snackbar.cardapio.application.usecases.BuscarProdutoPorIdUseCase;
import com.snackbar.clientes.application.usecases.BuscarAvaliacoesUseCase;
import com.snackbar.clientes.application.dto.ClienteAvaliacaoDTO;
import com.snackbar.pedidos.application.dto.ProdutoPopularDTO;
import com.snackbar.pedidos.application.ports.ProdutoPopularGatewayPort;
import com.snackbar.pedidos.infrastructure.persistence.PedidoEntity;
import com.snackbar.pedidos.infrastructure.persistence.ItemPedidoEntity;
import com.snackbar.pedidos.infrastructure.persistence.PedidoJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementação do gateway de produtos populares.
 * Combina dados de pedidos, produtos e avaliações.
 */
@Component
@RequiredArgsConstructor
public class ProdutoPopularGatewayAdapter implements ProdutoPopularGatewayPort {

    private final PedidoJpaRepository pedidoRepository;
    private final BuscarProdutoPorIdUseCase buscarProdutoPorIdUseCase;
    private final BuscarAvaliacoesUseCase buscarAvaliacoesUseCase;

    @Override
    public List<ProdutoPopularDTO> buscarMaisPedidos(int limite) {
        // Busca todos os pedidos finalizados
        List<PedidoEntity> pedidos = pedidoRepository.findAll();

        // Conta quantas vezes cada produto foi pedido
        Map<String, Long> contagem = pedidos.stream()
                .flatMap(p -> p.getItens().stream())
                .collect(Collectors.groupingBy(
                        ItemPedidoEntity::getProdutoId,
                        Collectors.summingLong(ItemPedidoEntity::getQuantidade)));

        // Ordena por quantidade e pega os top N
        return contagem.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limite)
                .map(entry -> {
                    try {
                        ProdutoDTO produto = buscarProdutoPorIdUseCase.executar(entry.getKey());
                        return ProdutoPopularDTO.maisPedido(
                                produto.getId(),
                                produto.getNome(),
                                produto.getDescricao(),
                                produto.getPreco(),
                                produto.getFoto(),
                                produto.getCategoria(),
                                entry.getValue());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<ProdutoPopularDTO> buscarMaisPedidosPorCliente(String clienteId, int limite) {
        // Busca todos os pedidos do cliente específico
        List<PedidoEntity> pedidosCliente = pedidoRepository.findByClienteId(clienteId);

        // Conta quantas vezes cada produto foi pedido por este cliente
        Map<String, Long> contagem = pedidosCliente.stream()
                .flatMap(p -> p.getItens().stream())
                .collect(Collectors.groupingBy(
                        ItemPedidoEntity::getProdutoId,
                        Collectors.summingLong(ItemPedidoEntity::getQuantidade)));

        // Ordena por quantidade e pega os top N
        return contagem.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limite)
                .map(entry -> {
                    try {
                        ProdutoDTO produto = buscarProdutoPorIdUseCase.executar(entry.getKey());
                        return ProdutoPopularDTO.maisPedido(
                                produto.getId(),
                                produto.getNome(),
                                produto.getDescricao(),
                                produto.getPreco(),
                                produto.getFoto(),
                                produto.getCategoria(),
                                entry.getValue());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<ProdutoPopularDTO> buscarBemAvaliados(int limite) {
        // Busca todos os produtos que têm avaliação
        // Como não temos endpoint para listar todos produtos, vamos usar os pedidos
        // como base
        List<PedidoEntity> pedidos = pedidoRepository.findAll();

        Set<String> produtoIds = pedidos.stream()
                .flatMap(p -> p.getItens().stream())
                .map(ItemPedidoEntity::getProdutoId)
                .collect(Collectors.toSet());

        // Para cada produto, busca as avaliações e calcula a média
        List<ProdutoComAvaliacao> produtosComAvaliacao = produtoIds.stream()
                .map(produtoId -> {
                    try {
                        ProdutoDTO produto = buscarProdutoPorIdUseCase.executar(produtoId);
                        List<ClienteAvaliacaoDTO> avaliacoes = buscarAvaliacoesUseCase.buscarPorProduto(produtoId);

                        if (avaliacoes.isEmpty()) {
                            return null;
                        }

                        double media = avaliacoes.stream()
                                .mapToInt(ClienteAvaliacaoDTO::getNota)
                                .average()
                                .orElse(0.0);

                        return new ProdutoComAvaliacao(produto, media, avaliacoes.size());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(ProdutoComAvaliacao::media).reversed())
                .limit(limite)
                .toList();

        return produtosComAvaliacao.stream()
                .map(p -> ProdutoPopularDTO.bemAvaliado(
                        p.produto().getId(),
                        p.produto().getNome(),
                        p.produto().getDescricao(),
                        p.produto().getPreco(),
                        p.produto().getFoto(),
                        p.produto().getCategoria(),
                        p.media(),
                        p.totalAvaliacoes()))
                .toList();
    }

    private record ProdutoComAvaliacao(ProdutoDTO produto, double media, int totalAvaliacoes) {
    }
}
