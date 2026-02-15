package com.snackbar.pedidos.application.services;

import com.snackbar.pedidos.application.dto.*;
import com.snackbar.pedidos.application.ports.CardapioServicePort;
import com.snackbar.pedidos.application.ports.PedidoPendenteRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço que gerencia a fila de pedidos de totem (auto atendimento) aguardando aceitação.
 * Quando o operador finaliza um pedido no totem, o pedido vai para esta fila.
 * Um funcionário no painel pode aceitar ou rejeitar; ao aceitar, o pedido real é criado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FilaPedidosTotemService {

    private final CardapioServicePort cardapioService;
    private final PedidoPendenteRepositoryPort pedidoPendenteRepository;

    private static final long TEMPO_MAXIMO_FILA_MINUTOS = 30;

    /**
     * Adiciona um pedido à fila de pendentes do totem.
     */
    @Transactional
    public PedidoPendenteDTO adicionarPedido(CriarPedidoAutoAtendimentoRequest request) {
        String pedidoId = UUID.randomUUID().toString();
        List<ItemPedidoPendenteDTO> itens = new ArrayList<>();
        BigDecimal valorTotal = BigDecimal.ZERO;

        for (ItemPedidoRequest itemReq : request.getItens()) {
            var produto = cardapioService.buscarProdutoPorId(itemReq.getProdutoId());
            BigDecimal precoUnitario = produto.getPreco();

            List<AdicionalPedidoPendenteDTO> adicionaisDTO = new ArrayList<>();
            BigDecimal subtotalAdicionais = BigDecimal.ZERO;

            if (itemReq.getAdicionais() != null && !itemReq.getAdicionais().isEmpty()) {
                for (ItemPedidoAdicionalRequest adicionalReq : itemReq.getAdicionais()) {
                    var adicional = cardapioService.buscarAdicionalPorId(adicionalReq.getAdicionalId());
                    BigDecimal precoAdicional = adicional.getPreco();
                    BigDecimal subtotalAdicional = precoAdicional
                            .multiply(BigDecimal.valueOf(adicionalReq.getQuantidade()));

                    adicionaisDTO.add(AdicionalPedidoPendenteDTO.builder()
                            .adicionalId(adicionalReq.getAdicionalId())
                            .nome(adicional.getNome())
                            .quantidade(adicionalReq.getQuantidade())
                            .precoUnitario(precoAdicional)
                            .subtotal(subtotalAdicional)
                            .build());

                    subtotalAdicionais = subtotalAdicionais.add(subtotalAdicional);
                }
            }

            BigDecimal precoComAdicionais = precoUnitario.add(subtotalAdicionais);
            BigDecimal subtotal = precoComAdicionais.multiply(BigDecimal.valueOf(itemReq.getQuantidade()));

            itens.add(ItemPedidoPendenteDTO.builder()
                    .produtoId(itemReq.getProdutoId())
                    .nomeProduto(produto.getNome())
                    .quantidade(itemReq.getQuantidade())
                    .precoUnitario(precoUnitario)
                    .subtotal(subtotal)
                    .observacoes(itemReq.getObservacoes())
                    .adicionais(adicionaisDTO.isEmpty() ? null : adicionaisDTO)
                    .build());

            valorTotal = valorTotal.add(subtotal);
        }

        String nomeCliente = request.getNomeCliente() != null && !request.getNomeCliente().isBlank()
                ? request.getNomeCliente()
                : "Cliente Totem";

        PedidoPendenteDTO pedidoPendente = PedidoPendenteDTO.builder()
                .id(pedidoId)
                .tipo(PedidoPendenteDTO.TIPO_TOTEM)
                .mesaToken(null)
                .mesaId(null)
                .numeroMesa(null)
                .clienteId(null)
                .nomeCliente(nomeCliente)
                .itens(itens)
                .meiosPagamento(request.getMeiosPagamento())
                .observacoes(request.getObservacao())
                .valorTotal(valorTotal)
                .dataHoraSolicitacao(LocalDateTime.now())
                .tempoEsperaSegundos(0)
                .build();

        PedidoPendenteDTO salvo = pedidoPendenteRepository.salvar(pedidoPendente);

        log.info("Pedido totem adicionado à fila - ID: {}, Cliente: {}", pedidoId, nomeCliente);

        return salvo;
    }

    @Transactional
    public List<PedidoPendenteDTO> listarPedidosPendentes() {
        pedidoPendenteRepository.removerExpirados(TEMPO_MAXIMO_FILA_MINUTOS);

        return pedidoPendenteRepository.listarPendentesPorTipo(PedidoPendenteDTO.TIPO_TOTEM).stream()
                .peek(PedidoPendenteDTO::atualizarTempoEspera)
                .sorted(Comparator.comparing(PedidoPendenteDTO::getDataHoraSolicitacao))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<PedidoPendenteDTO> buscarPorId(String pedidoId) {
        return pedidoPendenteRepository.buscarPendentePorId(pedidoId)
                .filter(p -> PedidoPendenteDTO.TIPO_TOTEM.equals(p.getTipo()))
                .map(pedido -> {
                    pedido.atualizarTempoEspera();
                    return pedido;
                });
    }

    /**
     * Busca e remove atomicamente um pedido totem da fila (para aceitação).
     * Garante que o pedido seja do tipo TOTEM.
     */
    @Transactional
    public Optional<PedidoPendenteDTO> buscarERemoverAtomicamente(String pedidoId) {
        Optional<PedidoPendenteDTO> pedidoOpt = pedidoPendenteRepository.buscarPendentePorIdComLock(pedidoId);

        if (pedidoOpt.isPresent()) {
            PedidoPendenteDTO pedido = pedidoOpt.get();
            if (!PedidoPendenteDTO.TIPO_TOTEM.equals(pedido.getTipo())) {
                log.warn("Pedido {} não é do tipo TOTEM (tipo: {}), ignorando aceitação totem", pedidoId, pedido.getTipo());
                return Optional.empty();
            }
            pedido.atualizarTempoEspera();
            log.info("Pedido totem obtido para aceitação (com lock) - ID: {}", pedidoId);
            return Optional.of(pedido);
        }

        return Optional.empty();
    }

    @Transactional
    public PedidoPendenteDTO removerPedido(String pedidoId) {
        Optional<PedidoPendenteDTO> pedidoOpt = buscarPorId(pedidoId);
        if (pedidoOpt.isPresent()) {
            pedidoPendenteRepository.remover(pedidoId);
            log.info("Pedido totem removido da fila - ID: {}", pedidoId);
            return pedidoOpt.get();
        }
        return null;
    }

    @Transactional
    public void registrarConversaoParaPedidoReal(String pedidoPendenteId, String pedidoRealId) {
        if (pedidoPendenteId != null && pedidoRealId != null) {
            pedidoPendenteRepository.marcarComoAceito(pedidoPendenteId, pedidoRealId);
            log.info("Mapeado pedido pendente totem {} -> pedido real {}", pedidoPendenteId, pedidoRealId);
        }
    }

    @Transactional(readOnly = true)
    public Optional<String> buscarPedidoRealPorPendente(String pedidoPendenteId) {
        return pedidoPendenteRepository.buscarPedidoRealPorPendente(pedidoPendenteId);
    }

    @Transactional
    public int quantidadePedidosPendentes() {
        pedidoPendenteRepository.removerExpirados(TEMPO_MAXIMO_FILA_MINUTOS);
        return (int) pedidoPendenteRepository.contarPendentesPorTipo(PedidoPendenteDTO.TIPO_TOTEM);
    }
}
