package com.snackbar.pedidos.application.usecases;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.*;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.services.AuditoriaPagamentoService;
import com.snackbar.pedidos.application.services.AuditoriaPagamentoService.ContextoRequisicao;
import com.snackbar.pedidos.application.services.FilaPedidosMesaService;
import com.snackbar.pedidos.application.services.GeradorNumeroPedidoService;
import com.snackbar.pedidos.application.services.ValidadorStatusLoja;
import com.snackbar.pedidos.domain.entities.ItemPedido;
import com.snackbar.pedidos.domain.entities.ItemPedidoAdicional;
import com.snackbar.pedidos.domain.entities.MeioPagamentoPedido;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.valueobjects.NumeroPedido;
import com.snackbar.kernel.domain.exceptions.ConflitoException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case para funcionário aceitar um pedido pendente de mesa.
 * 
 * Quando o funcionário aceita, o pedido é:
 * 1. Removido atomicamente da fila de pendentes (thread-safe)
 * 2. Criado como pedido real no sistema (com retry em caso de conflito)
 * 3. Vinculado ao usuário que aceitou
 * 4. Colocado no status PENDENTE para preparação
 * 
 * PROTEÇÕES DE CONCORRÊNCIA:
 * - Remoção atômica da fila evita que dois funcionários aceitem o mesmo pedido
 * - Geração de número via sequence atômica elimina conflitos
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AceitarPedidoMesaUseCase {

    private final FilaPedidosMesaService filaPedidosMesa;
    private final PedidoRepositoryPort pedidoRepository;
    private final ValidadorStatusLoja validadorStatusLoja;
    private final GeradorNumeroPedidoService geradorNumeroPedido;
    private final AuditoriaPagamentoService auditoriaPagamentoService;

    @Transactional
    public PedidoDTO executar(String pedidoPendenteId, String usuarioId, @Nullable ContextoRequisicao contexto) {
        if (contexto == null) {
            contexto = ContextoRequisicao.vazio();
        }

        // Valida parâmetros
        if (pedidoPendenteId == null || pedidoPendenteId.isBlank()) {
            throw new ValidationException("ID do pedido pendente é obrigatório");
        }
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new ValidationException("ID do usuário é obrigatório");
        }

        // Sem sessão o pedido nasceria órfão (fora do caixa e do fechamento): 422 e o item fica na fila.
        // Trava a sessão antes da linha da fila — mesma ordem do fechamento, sem deadlock.
        String sessaoId = validadorStatusLoja.exigirSessaoAtiva().getId();

        // Busca e remove atomicamente o pedido da fila (thread-safe)
        // Isso garante que apenas um funcionário consiga aceitar o mesmo pedido
        PedidoPendenteDTO pedidoPendente = filaPedidosMesa.buscarERemoverAtomicamente(pedidoPendenteId)
                .orElseThrow(() -> new ConflitoException(
                        "Pedido pendente não encontrado ou já foi aceito/expirado: " + pedidoPendenteId));

        // Cria o pedido real a partir do pendente
        return criarPedidoReal(pedidoPendente, usuarioId, pedidoPendenteId, contexto, sessaoId);
    }

    /**
     * Método de compatibilidade para chamadas sem contexto.
     */
    @Transactional
    public PedidoDTO executar(String pedidoPendenteId, String usuarioId) {
        return executar(pedidoPendenteId, usuarioId, null);
    }

    private PedidoDTO criarPedidoReal(
            PedidoPendenteDTO pedidoPendente,
            String usuarioId,
            String pedidoPendenteId,
            ContextoRequisicao contexto,
            String sessaoId) {
        // Gera número do pedido usando o serviço dedicado
        NumeroPedido numeroPedido = geradorNumeroPedido.gerarProximoNumero();

        // Cria o pedido real vinculado ao funcionário que aceitou
        Pedido pedido = Pedido.criar(
                numeroPedido,
                pedidoPendente.getClienteId(),
                pedidoPendente.getNomeCliente(),
                usuarioId);

        // Define a mesa
        pedido.definirMesa(pedidoPendente.getMesaId(), pedidoPendente.getNumeroMesa(), pedidoPendente.getNomeCliente());
        pedido.definirPiso(pedidoPendente.getPiso());

        // Adiciona os itens
        for (ItemPedidoPendenteDTO itemPendente : pedidoPendente.getItens()) {
            Preco precoUnitario = Preco.of(itemPendente.getPrecoUnitario());

            ItemPedido item = ItemPedido.criar(
                    itemPendente.getProdutoId(),
                    itemPendente.getNomeProduto(),
                    itemPendente.getQuantidade(),
                    precoUnitario,
                    itemPendente.getObservacoes());

            // Adiciona os adicionais ao item
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

        // Adiciona observações
        if (pedidoPendente.getObservacoes() != null && !pedidoPendente.getObservacoes().isBlank()) {
            pedido.atualizarObservacoes(pedidoPendente.getObservacoes());
        }

        // Adiciona meios de pagamento do pedido pendente
        if (pedidoPendente.getMeiosPagamento() != null) {
            for (MeioPagamentoRequest mpRequest : pedidoPendente.getMeiosPagamento()) {
                Preco valor = Preco.of(mpRequest.getValor());
                MeioPagamentoPedido meioPagamento = criarMeioPagamentoComTroco(mpRequest, valor);
                pedido.adicionarMeioPagamento(meioPagamento);
            }
            log.debug("Adicionados {} meios de pagamento ao pedido",
                    pedidoPendente.getMeiosPagamento().size());
        }

        pedido.definirSessaoId(sessaoId);

        // Salva o pedido
        Pedido pedidoSalvo = pedidoRepository.salvar(pedido);

        // Registra auditoria do pagamento (assíncrono via @Async)
        if (!pedidoSalvo.getMeiosPagamento().isEmpty()) {
            try {
                auditoriaPagamentoService.registrarPagamentoMesa(pedidoSalvo, contexto);
            } catch (Exception e) {
                log.warn("Falha ao registrar auditoria de pagamento mesa (não-crítico): {}", e.getMessage());
            }
        }

        // Nota: A remoção da fila já foi feita atomicamente em
        // buscarERemoverAtomicamente()
        // Registra mapeamento pendente -> pedido real para que o cliente acompanhe o
        // status
        filaPedidosMesa.registrarConversaoParaPedidoReal(pedidoPendenteId, pedidoSalvo.getId());

        log.info("Pedido aceito - Número: {}, Mesa: {}, Usuário: {}, Cliente: {}",
                pedidoSalvo.getNumeroPedido().getNumero(),
                pedidoPendente.getNumeroMesa(),
                usuarioId,
                pedidoPendente.getNomeCliente());

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
