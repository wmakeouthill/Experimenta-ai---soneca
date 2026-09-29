package com.snackbar.pedidos.application.usecases;

import java.util.ArrayList;
import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.pedidos.application.dto.*;
import com.snackbar.pedidos.application.ports.CardapioServicePort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.services.AuditoriaPagamentoService;
import com.snackbar.pedidos.application.services.AuditoriaPagamentoService.ContextoRequisicao;
import com.snackbar.pedidos.application.services.GeradorNumeroPedidoService;
import com.snackbar.pedidos.application.services.ValidadorStatusLoja;
import com.snackbar.pedidos.domain.entities.ItemPedido;
import com.snackbar.pedidos.domain.entities.ItemPedidoAdicional;
import com.snackbar.pedidos.domain.entities.MeioPagamentoPedido;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.services.PedidoValidator;
import com.snackbar.pedidos.domain.valueobjects.NumeroPedido;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case para criação de pedidos com tratamento de concorrência.
 * 
 * Implementa retry automático em caso de conflito de número de pedido,
 * garantindo que pedidos concorrentes não falhem por duplicação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CriarPedidoUseCase {

    private final PedidoRepositoryPort pedidoRepository;
    private final CardapioServicePort cardapioService;
    private final PedidoValidator pedidoValidator;
    private final ValidadorStatusLoja validadorStatusLoja;
    private final GeradorNumeroPedidoService geradorNumeroPedido;
    private final AuditoriaPagamentoService auditoriaPagamentoService;

    @Transactional
    public PedidoDTO executar(CriarPedidoRequest request, @Nullable ContextoRequisicao contexto) {
        if (contexto == null) {
            contexto = ContextoRequisicao.vazio();
        }

        return executarCriacao(request, contexto);
    }

    /**
     * Método de compatibilidade para chamadas sem contexto.
     */
    @Transactional
    public PedidoDTO executar(CriarPedidoRequest request) {
        return executar(request, null);
    }

    private PedidoDTO executarCriacao(CriarPedidoRequest request, ContextoRequisicao contexto) {
        // Antes de gerar o número: loja fechada não consome sequência; pausada ainda aceita balcão
        String sessaoId = validadorStatusLoja.exigirSessaoAtiva().getId();
        List<ItemPedido> itens = montarItens(request.getItens());
        NumeroPedido numeroPedido = geradorNumeroPedido.gerarProximoNumero();

        Pedido pedido = Pedido.criar(
                numeroPedido,
                request.getClienteId(),
                request.getClienteNome(),
                request.getUsuarioId());
        pedido.definirPiso(request.getPiso());
        itens.forEach(pedido::adicionarItem);

        pedido.atualizarObservacoes(request.getObservacoes());

        for (MeioPagamentoRequest meioPagamentoRequest : request.getMeiosPagamento()) {
            Preco valor = Preco.of(meioPagamentoRequest.getValor());
            MeioPagamentoPedido meioPagamentoPedido = criarMeioPagamentoComTroco(meioPagamentoRequest, valor);
            pedido.adicionarMeioPagamento(meioPagamentoPedido);
        }

        validarTotalMeiosPagamento(pedido);
        pedidoValidator.validarCriacao(pedido);

        pedido.definirSessaoId(sessaoId);

        Pedido pedidoSalvo = pedidoRepository.salvar(pedido);

        // Registra auditoria do pagamento (assíncrono via @Async)
        if (!pedidoSalvo.getMeiosPagamento().isEmpty()) {
            try {
                auditoriaPagamentoService.registrarPagamentoCriacaoPedido(pedidoSalvo, contexto);
            } catch (Exception e) {
                log.warn("Falha ao registrar auditoria de pagamento criação (não-crítico): {}", e.getMessage());
            }
        }

        return PedidoDTO.de(pedidoSalvo);
    }

    /**
     * Monta os itens checando produto e adicional disponíveis. Roda antes de gerar o número: a sequência
     * é REQUIRES_NEW, então uma recusa depois dela deixaria lacuna na numeração.
     */
    private List<ItemPedido> montarItens(List<ItemPedidoRequest> itensRequest) {
        List<ItemPedido> itens = new ArrayList<>();
        for (ItemPedidoRequest itemRequest : itensRequest) {
            var produtoDTO = cardapioService.buscarProdutoDisponivel(itemRequest.getProdutoId());
            Preco precoUnitario = Preco.of(produtoDTO.getPreco());

            List<ItemPedidoAdicional> adicionais = processarAdicionais(itemRequest.getAdicionais());

            itens.add(ItemPedido.criar(
                    itemRequest.getProdutoId(),
                    produtoDTO.getNome(),
                    itemRequest.getQuantidade(),
                    precoUnitario,
                    itemRequest.getObservacoes(),
                    adicionais));
        }
        return itens;
    }

    private void validarTotalMeiosPagamento(Pedido pedido) {
        Preco totalMeiosPagamento = pedido.calcularTotalMeiosPagamento();
        if (!totalMeiosPagamento.equals(pedido.getValorTotal())) {
            throw new com.snackbar.kernel.domain.exceptions.ValidationException(
                    String.format(
                            "A soma dos meios de pagamento (R$ %.2f) deve ser igual ao valor total do pedido (R$ %.2f)",
                            totalMeiosPagamento.getAmount().doubleValue(),
                            pedido.getValorTotal().getAmount().doubleValue()));
        }
    }

    /**
     * Processa a lista de adicionais do request, validando e buscando informações
     * completas.
     */
    private List<ItemPedidoAdicional> processarAdicionais(List<ItemPedidoAdicionalRequest> adicionaisRequest) {
        if (adicionaisRequest == null || adicionaisRequest.isEmpty()) {
            return new ArrayList<>();
        }

        List<ItemPedidoAdicional> adicionais = new ArrayList<>();
        for (ItemPedidoAdicionalRequest adicionalRequest : adicionaisRequest) {
            var adicionalDTO = cardapioService.buscarAdicionalDisponivel(adicionalRequest.getAdicionalId());
            Preco precoUnitario = Preco.of(adicionalDTO.getPreco());

            ItemPedidoAdicional adicional = ItemPedidoAdicional.criar(
                    adicionalRequest.getAdicionalId(),
                    adicionalDTO.getNome(),
                    adicionalRequest.getQuantidade(),
                    precoUnitario);

            adicionais.add(adicional);
        }
        return adicionais;
    }

    private MeioPagamentoPedido criarMeioPagamentoComTroco(MeioPagamentoRequest request, Preco valor) {
        if (request.getMeioPagamento() == com.snackbar.pedidos.domain.entities.MeioPagamento.DINHEIRO
                && request.getValorPagoDinheiro() != null) {
            return MeioPagamentoPedido.criarComTroco(valor, Preco.of(request.getValorPagoDinheiro()));
        }
        return MeioPagamentoPedido.criar(request.getMeioPagamento(), valor);
    }
}
