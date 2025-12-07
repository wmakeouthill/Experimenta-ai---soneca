package com.snackbar.pedidos.application.usecases;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.ItemPedidoPendenteDTO;
import com.snackbar.pedidos.application.dto.PedidoDTO;
import com.snackbar.pedidos.application.dto.PedidoPendenteDTO;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import com.snackbar.pedidos.application.services.FilaPedidosMesaService;
import com.snackbar.pedidos.domain.entities.ItemPedido;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.valueobjects.NumeroPedido;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case para funcionário aceitar um pedido pendente de mesa.
 * 
 * Quando o funcionário aceita, o pedido é:
 * 1. Removido da fila de pendentes
 * 2. Criado como pedido real no sistema
 * 3. Vinculado ao usuário que aceitou
 * 4. Colocado no status PENDENTE para preparação
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AceitarPedidoMesaUseCase {

    private final FilaPedidosMesaService filaPedidosMesa;
    private final PedidoRepositoryPort pedidoRepository;
    private final SessaoTrabalhoRepositoryPort sessaoTrabalhoRepository;

    @Transactional
    public PedidoDTO executar(String pedidoPendenteId, String usuarioId) {
        // Valida parâmetros
        if (pedidoPendenteId == null || pedidoPendenteId.isBlank()) {
            throw new ValidationException("ID do pedido pendente é obrigatório");
        }
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new ValidationException("ID do usuário é obrigatório");
        }

        // Busca e remove o pedido da fila
        PedidoPendenteDTO pedidoPendente = filaPedidosMesa.buscarPorId(pedidoPendenteId)
                .orElseThrow(() -> new ValidationException(
                        "Pedido pendente não encontrado ou já foi aceito/expirado: " + pedidoPendenteId));

        // Gera número do pedido
        int ultimoNumero = pedidoRepository.buscarUltimoNumeroPedido();
        NumeroPedido numeroPedido = ultimoNumero == 0
                ? NumeroPedido.gerarPrimeiro()
                : NumeroPedido.gerarProximo(ultimoNumero);

        // Cria o pedido real vinculado ao funcionário que aceitou
        Pedido pedido = Pedido.criar(
                numeroPedido,
                pedidoPendente.getClienteId(),
                pedidoPendente.getNomeCliente(),
                usuarioId);

        // Define a mesa
        pedido.definirMesa(pedidoPendente.getMesaId(), pedidoPendente.getNumeroMesa(), pedidoPendente.getNomeCliente());

        // Adiciona os itens
        for (ItemPedidoPendenteDTO itemPendente : pedidoPendente.getItens()) {
            Preco precoUnitario = Preco.of(itemPendente.getPrecoUnitario());

            ItemPedido item = ItemPedido.criar(
                    itemPendente.getProdutoId(),
                    itemPendente.getNomeProduto(),
                    itemPendente.getQuantidade(),
                    precoUnitario,
                    itemPendente.getObservacoes());

            pedido.adicionarItem(item);
        }

        // Adiciona observações
        if (pedidoPendente.getObservacoes() != null && !pedidoPendente.getObservacoes().isBlank()) {
            pedido.atualizarObservacoes(pedidoPendente.getObservacoes());
        }

        // Vincula sessão de trabalho ativa
        sessaoTrabalhoRepository.buscarSessaoAtiva()
                .ifPresent(sessao -> pedido.definirSessaoId(sessao.getId()));

        // Salva o pedido
        Pedido pedidoSalvo = pedidoRepository.salvar(pedido);

        // Remove da fila somente após salvar com sucesso
        filaPedidosMesa.removerPedido(pedidoPendenteId);

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
}
