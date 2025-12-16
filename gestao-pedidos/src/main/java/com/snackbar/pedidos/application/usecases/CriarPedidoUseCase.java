package com.snackbar.pedidos.application.usecases;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.pedidos.application.dto.CriarPedidoRequest;
import com.snackbar.pedidos.application.dto.ItemPedidoRequest;
import com.snackbar.pedidos.application.dto.MeioPagamentoRequest;
import com.snackbar.pedidos.application.dto.PedidoDTO;
import com.snackbar.pedidos.application.ports.CardapioServicePort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import com.snackbar.pedidos.application.services.GeradorNumeroPedidoService;
import com.snackbar.pedidos.domain.entities.ItemPedido;
import com.snackbar.pedidos.domain.entities.MeioPagamentoPedido;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.services.PedidoValidator;
import com.snackbar.pedidos.domain.valueobjects.NumeroPedido;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SessaoTrabalhoRepositoryPort sessaoTrabalhoRepository;
    private final GeradorNumeroPedidoService geradorNumeroPedido;

    private static final int MAX_TENTATIVAS_CONCORRENCIA = 3;

    @Transactional
    public PedidoDTO executar(CriarPedidoRequest request) {
        int tentativas = 0;
        DataIntegrityViolationException ultimaExcecao = null;

        while (tentativas < MAX_TENTATIVAS_CONCORRENCIA) {
            tentativas++;

            try {
                return executarCriacao(request);
            } catch (DataIntegrityViolationException e) {
                if (geradorNumeroPedido.isDuplicacaoNumeroPedido(e)) {
                    log.warn("Conflito de número de pedido detectado (tentativa {}), tentando novamente...",
                            tentativas);
                    ultimaExcecao = e;
                    // Continua o loop para tentar novamente
                } else {
                    throw e; // Outro tipo de violação, propaga
                }
            }
        }

        log.error("Falha ao criar pedido após {} tentativas por conflito de número",
                MAX_TENTATIVAS_CONCORRENCIA);
        throw new IllegalStateException(
                "Não foi possível criar o pedido após " + MAX_TENTATIVAS_CONCORRENCIA +
                        " tentativas devido a alta concorrência",
                ultimaExcecao);
    }

    private PedidoDTO executarCriacao(CriarPedidoRequest request) {
        NumeroPedido numeroPedido = geradorNumeroPedido.gerarProximoNumero();

        Pedido pedido = Pedido.criar(
                numeroPedido,
                request.getClienteId(),
                request.getClienteNome(),
                request.getUsuarioId());

        for (ItemPedidoRequest itemRequest : request.getItens()) {
            validarProdutoDisponivel(itemRequest.getProdutoId());

            var produtoDTO = cardapioService.buscarProdutoPorId(itemRequest.getProdutoId());
            Preco precoUnitario = Preco.of(produtoDTO.getPreco());

            ItemPedido item = ItemPedido.criar(
                    itemRequest.getProdutoId(),
                    produtoDTO.getNome(),
                    itemRequest.getQuantidade(),
                    precoUnitario,
                    itemRequest.getObservacoes());

            pedido.adicionarItem(item);
        }

        pedido.atualizarObservacoes(request.getObservacoes());

        for (MeioPagamentoRequest meioPagamentoRequest : request.getMeiosPagamento()) {
            Preco valor = Preco.of(meioPagamentoRequest.getValor());
            MeioPagamentoPedido meioPagamentoPedido = MeioPagamentoPedido.criar(
                    meioPagamentoRequest.getMeioPagamento(),
                    valor);
            pedido.adicionarMeioPagamento(meioPagamentoPedido);
        }

        validarTotalMeiosPagamento(pedido);
        pedidoValidator.validarCriacao(pedido);

        vincularSessaoAtiva(pedido);

        Pedido pedidoSalvo = pedidoRepository.salvar(pedido);

        return PedidoDTO.de(pedidoSalvo);
    }

    private void vincularSessaoAtiva(Pedido pedido) {
        sessaoTrabalhoRepository.buscarSessaoAtiva()
                .ifPresent(sessao -> pedido.definirSessaoId(sessao.getId()));
    }

    private void validarProdutoDisponivel(String produtoId) {
        if (!cardapioService.produtoEstaDisponivel(produtoId)) {
            throw new com.snackbar.kernel.domain.exceptions.ValidationException(
                    "Produto não está disponível: " + produtoId);
        }
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
}
