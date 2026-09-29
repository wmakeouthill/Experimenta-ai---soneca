package com.snackbar.pedidos.infrastructure.gateway;

import com.snackbar.cardapio.application.dto.ProdutoDTO;
import com.snackbar.cardapio.application.usecases.BuscarProdutoPorIdUseCase;
import com.snackbar.clientes.application.usecases.BuscarAvaliacoesUseCase;
import com.snackbar.clientes.application.ports.ClienteFavoritoRepositoryPort;
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
 * Exclui bebidas dos carrosséis para destacar pratos principais.
 */
@Component
@RequiredArgsConstructor
public class ProdutoPopularGatewayAdapter implements ProdutoPopularGatewayPort {

    private static final String CATEGORIA_BEBIDAS = "Bebidas";

    private final PedidoJpaRepository pedidoRepository;
    private final BuscarProdutoPorIdUseCase buscarProdutoPorIdUseCase;
    private final BuscarAvaliacoesUseCase buscarAvaliacoesUseCase;
    private final ClienteFavoritoRepositoryPort favoritoRepository;

    /**
     * Bebidas saem dos carrosséis; produto desativado também (o cliente tocaria nele e o pedido
     * seria recusado).
     */
    private boolean foraDoCarrossel(ProdutoDTO produto) {
        return !produto.isDisponivel() || CATEGORIA_BEBIDAS.equalsIgnoreCase(produto.getCategoria());
    }

    @Override
    public List<ProdutoPopularDTO> buscarMaisPedidos(int limite) {
        Map<String, Long> contagem = somarQuantidadePorProduto();

        // Ordena por quantidade, filtra bebidas e pega os top N
        return contagem.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    try {
                        ProdutoDTO produto = buscarProdutoPorIdUseCase.executar(entry.getKey());
                        if (foraDoCarrossel(produto)) {
                            return null;
                        }
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
                .limit(limite)
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

        // Ordena por quantidade, filtra bebidas e pega os top N
        return contagem.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    try {
                        ProdutoDTO produto = buscarProdutoPorIdUseCase.executar(entry.getKey());
                        if (foraDoCarrossel(produto)) {
                            return null;
                        }
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
                .limit(limite)
                .toList();
    }

    @Override
    public List<ProdutoPopularDTO> buscarBemAvaliados(int limite) {
        // Candidatos: produtos que já foram pedidos
        Set<String> produtoIds = somarQuantidadePorProduto().keySet();

        // Para cada produto, busca as avaliações e calcula a média (excluindo bebidas)
        List<ProdutoComAvaliacao> produtosComAvaliacao = produtoIds.stream()
                .map(produtoId -> {
                    try {
                        ProdutoDTO produto = buscarProdutoPorIdUseCase.executar(produtoId);

                        if (foraDoCarrossel(produto)) {
                            return null;
                        }

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

    @Override
    public List<ProdutoPopularDTO> buscarMaisFavoritados(int limite) {
        // Busca todos os produtos favoritados com contagem
        Map<String, Long> maisFavoritados = favoritoRepository.buscarMaisFavoritados();

        // Transforma em lista de DTOs, filtrando bebidas
        return maisFavoritados.entrySet().stream()
                .map(entry -> {
                    try {
                        ProdutoDTO produto = buscarProdutoPorIdUseCase.executar(entry.getKey());
                        if (foraDoCarrossel(produto)) {
                            return null;
                        }
                        return ProdutoPopularDTO.maisFavoritado(
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
                .limit(limite)
                .toList();
    }

    private Map<String, Long> somarQuantidadePorProduto() {
        return pedidoRepository.somarQuantidadePorProduto().stream()
                .collect(Collectors.toMap(linha -> (String) linha[0], linha -> ((Number) linha[1]).longValue()));
    }

    private record ProdutoComAvaliacao(ProdutoDTO produto, double media, int totalAvaliacoes) {
    }
}
