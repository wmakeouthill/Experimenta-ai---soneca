package com.snackbar.pedidos.application.usecases;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.*;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import com.snackbar.pedidos.application.services.AuditoriaPagamentoService;
import com.snackbar.pedidos.application.services.AuditoriaPagamentoService.ContextoRequisicao;
import com.snackbar.pedidos.application.services.FilaPedidosTotemService;
import com.snackbar.pedidos.application.services.GeradorNumeroPedidoService;
import com.snackbar.pedidos.domain.entities.ItemPedido;
import com.snackbar.pedidos.domain.entities.ItemPedidoAdicional;
import com.snackbar.pedidos.domain.entities.MeioPagamentoPedido;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.services.PedidoValidator;
import com.snackbar.pedidos.domain.valueobjects.NumeroPedido;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case para funcionário aceitar um pedido pendente de totem.
 * Quando aceito, o pedido é criado como pedido real (status
 * PENDENTE/aguardando).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AceitarPedidoTotemUseCase {

    private final FilaPedidosTotemService filaPedidosTotem;
    private final PedidoRepositoryPort pedidoRepository;
    private final SessaoTrabalhoRepositoryPort sessaoTrabalhoRepository;
    private final GeradorNumeroPedidoService geradorNumeroPedido;
    private final PedidoValidator pedidoValidator;
    private final AuditoriaPagamentoService auditoriaPagamentoService;

    @Transactional
    public PedidoDTO executar(String pedidoPendenteId, String usuarioId, @Nullable ContextoRequisicao contexto) {
        if (contexto == null) {
            contexto = ContextoRequisicao.vazio();
        }

        if (pedidoPendenteId == null || pedidoPendenteId.isBlank()) {
            throw new ValidationException("ID do pedido pendente é obrigatório");
        }
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new ValidationException("ID do usuário é obrigatório");
        }

        PedidoPendenteDTO pedidoPendente = filaPedidosTotem.buscarERemoverAtomicamente(pedidoPendenteId)
                .orElseThrow(() -> new ValidationException(
                        "Pedido pendente não encontrado ou já foi aceito/expirado: " + pedidoPendenteId));

        return criarPedidoReal(pedidoPendente, usuarioId, pedidoPendenteId, contexto);
    }

    @Transactional
    public PedidoDTO executar(String pedidoPendenteId, String usuarioId) {
        return executar(pedidoPendenteId, usuarioId, null);
    }

    private PedidoDTO criarPedidoReal(
            PedidoPendenteDTO pedidoPendente,
            String usuarioId,
            String pedidoPendenteId,
            ContextoRequisicao contexto) {
        NumeroPedido numeroPedido = geradorNumeroPedido.gerarProximoNumero();
        String nomeCliente = pedidoPendente.getNomeCliente() != null ? pedidoPendente.getNomeCliente()
                : "Cliente Totem";

        Pedido pedido = Pedido.criarPedidoAutoAtendimento(numeroPedido, nomeCliente, usuarioId);

        for (ItemPedidoPendenteDTO itemPendente : pedidoPendente.getItens()) {
            Preco precoUnitario = Preco.of(itemPendente.getPrecoUnitario());

            ItemPedido item = ItemPedido.criar(
                    itemPendente.getProdutoId(),
                    itemPendente.getNomeProduto(),
                    itemPendente.getQuantidade(),
                    precoUnitario,
                    itemPendente.getObservacoes());

            if (itemPendente.getAdicionais() != null && !itemPendente.getAdicionais().isEmpty()) {
                for (AdicionalPedidoPendenteDTO adicionalPendente : itemPendente.getAdicionais()) {
                    ItemPedidoAdicional adicional = ItemPedidoAdicional.criar(
                            adicionalPendente.getAdicionalId(),
                            adicionalPendente.getNome(),
                            adicionalPendente.getQuantidade(),
                            Preco.of(adicionalPendente.getPrecoUnitario()));
                    item.adicionarAdicional(adicional);
                }
            }

            pedido.adicionarItem(item);
        }

        if (pedidoPendente.getObservacoes() != null && !pedidoPendente.getObservacoes().isBlank()) {
            pedido.atualizarObservacoes(pedidoPendente.getObservacoes());
        }

        if (pedidoPendente.getMeiosPagamento() != null) {
            for (MeioPagamentoRequest mpRequest : pedidoPendente.getMeiosPagamento()) {
                Preco valor = Preco.of(mpRequest.getValor());
                MeioPagamentoPedido meioPagamento = criarMeioPagamentoComTroco(mpRequest, valor);
                pedido.adicionarMeioPagamento(meioPagamento);
            }
        }

        sessaoTrabalhoRepository.buscarSessaoAtiva()
                .ifPresent(sessao -> pedido.definirSessaoId(sessao.getId()));

        pedidoValidator.validarCriacao(pedido);

        Pedido pedidoSalvo = pedidoRepository.salvar(pedido);

        if (!pedidoSalvo.getMeiosPagamento().isEmpty()) {
            try {
                auditoriaPagamentoService.registrarPagamentoAutoatendimento(pedidoSalvo, contexto);
            } catch (Exception e) {
                log.warn("Falha ao registrar auditoria de pagamento totem (não-crítico): {}", e.getMessage());
            }
        }

        filaPedidosTotem.registrarConversaoParaPedidoReal(pedidoPendenteId, pedidoSalvo.getId());

        log.info("Pedido totem aceito - Número: {}, Cliente: {}, Usuário: {}",
                pedidoSalvo.getNumeroPedido().getNumero(),
                nomeCliente,
                usuarioId);

        return PedidoDTO.de(pedidoSalvo);
    }

    private MeioPagamentoPedido criarMeioPagamentoComTroco(MeioPagamentoRequest request, Preco valor) {
        if (request.getMeioPagamento() == com.snackbar.pedidos.domain.entities.MeioPagamento.DINHEIRO
                && request.getValorPagoDinheiro() != null) {
            return MeioPagamentoPedido.criarComTroco(valor, Preco.of(request.getValorPagoDinheiro()));
        }
        return MeioPagamentoPedido.criar(request.getMeioPagamento(), valor);
    }
}
