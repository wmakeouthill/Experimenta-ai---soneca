package com.snackbar.pedidos.application.services;

import com.snackbar.pedidos.application.dto.*;
import com.snackbar.pedidos.application.ports.CardapioServicePort;
import com.snackbar.pedidos.application.ports.MesaRepositoryPort;
import com.snackbar.pedidos.domain.entities.Mesa;
import com.snackbar.pedidos.domain.exceptions.MesaNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Serviço que gerencia a fila de pedidos de mesa aguardando aceitação.
 * 
 * Quando um cliente faz um pedido via QR code, o pedido vai para esta fila.
 * Um funcionário logado pode ver os pedidos pendentes e aceitar,
 * momento em que o pedido é criado de verdade no sistema.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FilaPedidosMesaService {

    private final MesaRepositoryPort mesaRepository;
    private final CardapioServicePort cardapioService;

    // Cache em memória para pedidos pendentes
    // Chave: ID do pedido pendente (UUID)
    private final Map<String, PedidoPendenteDTO> filaPedidos = new ConcurrentHashMap<>();

    // Mapa para rastrear a conversão de pedidos pendentes em pedidos reais
    // Chave: ID do pedido pendente | Valor: ID do pedido real criado
    private final Map<String, String> mapaPendenteParaPedidoReal = new ConcurrentHashMap<>();

    // Tempo máximo que um pedido pode ficar na fila (30 minutos)
    private static final long TEMPO_MAXIMO_FILA_MINUTOS = 30;

    /**
     * Adiciona um pedido à fila de pendentes.
     * 
     * @param request Request do pedido vindo do cliente
     * @return DTO do pedido pendente criado
     */
    public PedidoPendenteDTO adicionarPedido(CriarPedidoMesaRequest request) {
        Mesa mesa = mesaRepository.buscarPorQrCodeToken(request.getMesaToken())
                .orElseThrow(() -> MesaNaoEncontradaException.porToken(request.getMesaToken()));

        String pedidoId = UUID.randomUUID().toString();

        // Busca informações dos produtos e calcula valores
        List<ItemPedidoPendenteDTO> itens = new ArrayList<>();
        BigDecimal valorTotal = BigDecimal.ZERO;

        for (ItemPedidoRequest itemReq : request.getItens()) {
            var produto = cardapioService.buscarProdutoPorId(itemReq.getProdutoId());
            BigDecimal precoUnitario = produto.getPreco();
            BigDecimal subtotal = precoUnitario.multiply(BigDecimal.valueOf(itemReq.getQuantidade()));

            itens.add(ItemPedidoPendenteDTO.builder()
                    .produtoId(itemReq.getProdutoId())
                    .nomeProduto(produto.getNome())
                    .quantidade(itemReq.getQuantidade())
                    .precoUnitario(precoUnitario)
                    .subtotal(subtotal)
                    .observacoes(itemReq.getObservacoes())
                    .build());

            valorTotal = valorTotal.add(subtotal);
        }

        PedidoPendenteDTO pedidoPendente = PedidoPendenteDTO.builder()
                .id(pedidoId)
                .mesaToken(request.getMesaToken())
                .mesaId(mesa.getId())
                .numeroMesa(mesa.getNumero())
                .clienteId(request.getClienteId())
                .nomeCliente(request.getNomeCliente())
                .itens(itens)
                .observacoes(request.getObservacoes())
                .valorTotal(valorTotal)
                .dataHoraSolicitacao(LocalDateTime.now())
                .tempoEsperaSegundos(0)
                .build();

        filaPedidos.put(pedidoId, pedidoPendente);

        log.info("Pedido adicionado à fila - ID: {}, Mesa: {}, Cliente: {}",
                pedidoId, mesa.getNumero(), request.getNomeCliente());

        return pedidoPendente;
    }

    /**
     * Lista todos os pedidos pendentes na fila, ordenados por tempo de espera.
     * Remove automaticamente pedidos expirados.
     */
    public List<PedidoPendenteDTO> listarPedidosPendentes() {
        removerPedidosExpirados();

        return filaPedidos.values().stream()
                .peek(PedidoPendenteDTO::atualizarTempoEspera)
                .sorted(Comparator.comparing(PedidoPendenteDTO::getDataHoraSolicitacao))
                .collect(Collectors.toList());
    }

    /**
     * Busca um pedido pendente pelo ID.
     */
    public Optional<PedidoPendenteDTO> buscarPorId(String pedidoId) {
        PedidoPendenteDTO pedido = filaPedidos.get(pedidoId);
        if (pedido != null) {
            pedido.atualizarTempoEspera();
        }
        return Optional.ofNullable(pedido);
    }

    /**
     * Busca e remove atomicamente um pedido da fila.
     * 
     * IMPORTANTE: Este método é thread-safe e garante que apenas um funcionário
     * consiga aceitar o mesmo pedido, evitando race conditions onde dois
     * funcionários poderiam aceitar o mesmo pedido simultaneamente.
     * 
     * @param pedidoId ID do pedido a ser buscado e removido
     * @return Optional contendo o pedido se encontrado e removido, ou empty se não
     *         existir
     */
    public synchronized Optional<PedidoPendenteDTO> buscarERemoverAtomicamente(String pedidoId) {
        PedidoPendenteDTO pedido = filaPedidos.remove(pedidoId);
        if (pedido != null) {
            pedido.atualizarTempoEspera();
            log.info("Pedido removido atomicamente da fila - ID: {}, Mesa: {}",
                    pedidoId, pedido.getNumeroMesa());
        }
        return Optional.ofNullable(pedido);
    }

    /**
     * Remove um pedido da fila (quando aceito ou rejeitado).
     */
    public PedidoPendenteDTO removerPedido(String pedidoId) {
        PedidoPendenteDTO removido = filaPedidos.remove(pedidoId);
        if (removido != null) {
            log.info("Pedido removido da fila - ID: {}", pedidoId);
        }
        return removido;
    }

    /**
     * Registra o mapeamento entre um pedido pendente e o pedido real criado ao
     * aceitá-lo.
     */
    public void registrarConversaoParaPedidoReal(String pedidoPendenteId, String pedidoRealId) {
        if (pedidoPendenteId != null && pedidoRealId != null) {
            mapaPendenteParaPedidoReal.put(pedidoPendenteId, pedidoRealId);
            log.info("Mapeado pedido pendente {} -> pedido real {}", pedidoPendenteId, pedidoRealId);
        }
    }

    /**
     * Obtém o ID do pedido real a partir do ID pendente, se existir.
     */
    public Optional<String> buscarPedidoRealPorPendente(String pedidoPendenteId) {
        return Optional.ofNullable(mapaPendenteParaPedidoReal.get(pedidoPendenteId));
    }

    /**
     * Retorna a quantidade de pedidos na fila.
     */
    public int quantidadePedidosPendentes() {
        removerPedidosExpirados();
        return filaPedidos.size();
    }

    /**
     * Verifica se há pedidos pendentes na fila.
     */
    public boolean existemPedidosPendentes() {
        return quantidadePedidosPendentes() > 0;
    }

    /**
     * Remove pedidos que estão na fila há mais tempo que o permitido.
     */
    private void removerPedidosExpirados() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(TEMPO_MAXIMO_FILA_MINUTOS);

        filaPedidos.entrySet().removeIf(entry -> {
            boolean expirado = entry.getValue().getDataHoraSolicitacao().isBefore(limite);
            if (expirado) {
                log.warn("Pedido expirado removido da fila - ID: {}, Mesa: {}",
                        entry.getKey(), entry.getValue().getNumeroMesa());
            }
            return expirado;
        });
    }

    /**
     * Limpa toda a fila (para testes ou reset).
     */
    public void limparFila() {
        filaPedidos.clear();
        log.info("Fila de pedidos limpa");
    }
}
