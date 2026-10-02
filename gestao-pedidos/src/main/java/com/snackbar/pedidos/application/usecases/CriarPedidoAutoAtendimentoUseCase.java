package com.snackbar.pedidos.application.usecases;

import java.util.ArrayList;
import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
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
 * Use case para criação de pedidos via auto atendimento (totem).
 * 
 * Diferente do pedido de mesa:
 * - Não requer cliente cadastrado (nome opcional)
 * - Não vai para fila de pendentes (operador já está logado)
 * - Pedido é criado diretamente no status PENDENTE
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CriarPedidoAutoAtendimentoUseCase {

    private final PedidoRepositoryPort pedidoRepository;
    private final CardapioServicePort cardapioService;
    private final PedidoValidator pedidoValidator;
    private final ValidadorStatusLoja validadorStatusLoja;
    private final GeradorNumeroPedidoService geradorNumeroPedido;
    private final AuditoriaPagamentoService auditoriaPagamentoService;

    @Transactional
    public PedidoAutoAtendimentoResponse executar(
            CriarPedidoAutoAtendimentoRequest request,
            String usuarioId,
            @Nullable ContextoRequisicao contexto) {

        if (contexto == null) {
            contexto = ContextoRequisicao.vazio();
        }

        return executarCriacao(request, usuarioId, contexto);
    }

    /**
     * Método de compatibilidade para chamadas sem contexto.
     */
    @Transactional
    public PedidoAutoAtendimentoResponse executar(CriarPedidoAutoAtendimentoRequest request, String usuarioId) {
        return executar(request, usuarioId, null);
    }

    private PedidoAutoAtendimentoResponse executarCriacao(
            CriarPedidoAutoAtendimentoRequest request,
            String usuarioId,
            ContextoRequisicao contexto) {
        // Totem é canal do cliente: loja pausada ou fechada recusa antes de gerar o número
        String sessaoId = validadorStatusLoja.exigirLojaAberta().getId();
        List<ItemPedido> itens = montarItens(request.getItens());
        NumeroPedido numeroPedido = geradorNumeroPedido.gerarProximoNumero();

        // Nome do cliente é opcional no auto atendimento - usado para chamar na tela de
        // espera
        String nomeCliente = request.getNomeCliente() != null && !request.getNomeCliente().isBlank()
                ? request.getNomeCliente()
                : "Cliente Totem";

        // Cria pedido sem cliente cadastrado (clienteId = null)
        // Apenas o nome para chamar na tela de espera
        Pedido pedido = Pedido.criarPedidoAutoAtendimento(
                numeroPedido,
                nomeCliente,
                usuarioId);
        pedido.definirPiso(request.getPiso());
        itens.forEach(pedido::adicionarItem);

        pedido.atualizarObservacoes(request.getObservacao());

        // Processa meios de pagamento quando o pedido ja chega pago.
        if (request.getMeiosPagamento() != null && !request.getMeiosPagamento().isEmpty()) {
            for (MeioPagamentoRequest meioPagamentoRequest : request.getMeiosPagamento()) {
                Preco valor = Preco.of(meioPagamentoRequest.getValor());
                MeioPagamentoPedido meioPagamentoPedido = criarMeioPagamentoComTroco(meioPagamentoRequest, valor);
                pedido.adicionarMeioPagamento(meioPagamentoPedido);
            }
            validarTotalMeiosPagamento(pedido);
        }

        pedidoValidator.validarCriacao(pedido);
        pedido.definirSessaoId(sessaoId);

        Pedido pedidoSalvo = pedidoRepository.salvar(pedido);

        // Registra auditoria do pagamento (assíncrono via @Async)
        if (!pedidoSalvo.getMeiosPagamento().isEmpty()) {
            try {
                auditoriaPagamentoService.registrarPagamentoAutoatendimento(pedidoSalvo, contexto);
            } catch (Exception e) {
                log.warn("Falha ao registrar auditoria de pagamento auto-atendimento (não-crítico): {}",
                        e.getMessage());
            }
        }

        log.info("[AUTO-ATENDIMENTO] Pedido criado - Número: {}, Cliente: {}, Valor: {}",
                pedidoSalvo.getNumeroPedido().getNumero(),
                nomeCliente,
                pedidoSalvo.getValorTotal().getAmount());

        return PedidoAutoAtendimentoResponse.builder()
                .id(pedidoSalvo.getId())
                .numeroPedido(pedidoSalvo.getNumeroPedido().getNumero())
                .numeroExibicao(pedidoSalvo.getNumeroExibicao())
                .nomeCliente(nomeCliente)
                .status(pedidoSalvo.getStatus().name())
                .valorTotal(pedidoSalvo.getValorTotal().getAmount())
                .dataPedido(pedidoSalvo.getDataPedido())
                .build();
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
            throw new ValidationException(
                    String.format(
                            "A soma dos meios de pagamento (R$ %.2f) deve ser igual ao valor total do pedido (R$ %.2f)",
                            totalMeiosPagamento.getAmount().doubleValue(),
                            pedido.getValorTotal().getAmount().doubleValue()));
        }
    }

    private List<ItemPedidoAdicional> processarAdicionais(List<ItemPedidoAdicionalRequest> adicionaisRequest) {
        if (adicionaisRequest == null || adicionaisRequest.isEmpty()) {
            return new ArrayList<>();
        }

        List<ItemPedidoAdicional> adicionais = new ArrayList<>();
        for (ItemPedidoAdicionalRequest adicionalRequest : adicionaisRequest) {
            var adicionalDTO = cardapioService.buscarAdicionalDisponivel(adicionalRequest.getAdicionalId());

            ItemPedidoAdicional adicional = ItemPedidoAdicional.criar(
                    adicionalRequest.getAdicionalId(),
                    adicionalDTO.getNome(),
                    adicionalRequest.getQuantidade(),
                    Preco.of(adicionalDTO.getPreco()));

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
