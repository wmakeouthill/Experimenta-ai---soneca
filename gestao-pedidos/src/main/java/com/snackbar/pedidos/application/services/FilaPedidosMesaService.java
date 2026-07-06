package com.snackbar.pedidos.application.services;

import com.snackbar.pedidos.application.dto.*;
import com.snackbar.pedidos.application.ports.CardapioServicePort;
import com.snackbar.pedidos.application.ports.MesaRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoPendenteRepositoryPort;
import com.snackbar.pedidos.domain.entities.Mesa;
import com.snackbar.pedidos.domain.exceptions.MesaNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço que gerencia a fila de pedidos de mesa aguardando aceitação.
 * 
 * Quando um cliente faz um pedido via QR code, o pedido vai para esta fila.
 * Um funcionário logado pode ver os pedidos pendentes e aceitar,
 * momento em que o pedido é criado de verdade no sistema.
 * 
 * PERSISTÊNCIA EM BANCO:
 * Os pedidos pendentes são armazenados em banco de dados para garantir
 * que não sejam perdidos em caso de restart do servidor e para suportar
 * múltiplas instâncias da aplicação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FilaPedidosMesaService {

    private final MesaRepositoryPort mesaRepository;
    private final CardapioServicePort cardapioService;
    private final PedidoPendenteRepositoryPort pedidoPendenteRepository;

    // Tempo máximo que um pedido pode ficar na fila (30 minutos)
    private static final long TEMPO_MAXIMO_FILA_MINUTOS = 30;

    /**
     * Adiciona um pedido à fila de pendentes.
     * 
     * @param request Request do pedido vindo do cliente
     * @return DTO do pedido pendente criado
     */
    @Transactional
    public PedidoPendenteDTO adicionarPedido(CriarPedidoMesaRequest request) {
        PedidoPendenteDTO pedido = montarPedidoPendente(request, false, null, request.getMeiosPagamento());
        PedidoPendenteDTO salvo = pedidoPendenteRepository.salvar(pedido);
        log.info("Pedido adicionado a fila (banco) - ID: {}, Mesa: {}, Cliente: {}",
                salvo.getId(), salvo.getNumeroMesa(), request.getNomeCliente());
        return salvo;
    }

    /**
     * Cria um pedido pendente OCULTO (aguardando aprovacao de PIX), com um unico
     * meio de pagamento PIX pelo valor total calculado no backend.
     */
    @Transactional
    public PedidoPendenteDTO adicionarPedidoAguardandoPagamento(
            CriarPedidoMesaRequest request, String pagamentoCorrelationId) {
        PedidoPendenteDTO pedido = montarPedidoPendente(request, true, pagamentoCorrelationId, null);
        // Substitui quaisquer meios enviados pelo cliente por um unico PIX do valor total.
        MeioPagamentoRequest pix = new MeioPagamentoRequest();
        pix.setMeioPagamento(com.snackbar.pedidos.domain.entities.MeioPagamento.PIX);
        pix.setValor(pedido.getValorTotal());
        pedido.setMeiosPagamento(java.util.List.of(pix));

        PedidoPendenteDTO salvo = pedidoPendenteRepository.salvar(pedido);
        log.info("Pedido pre-pago (oculto) adicionado - ID: {}, Mesa: {}, correlationId: {}",
                salvo.getId(), salvo.getNumeroMesa(), pagamentoCorrelationId);
        return salvo;
    }

    /**
     * Monta o DTO do pedido pendente calculando itens e valor total a partir do
     * cardapio. Nao persiste.
     */
    private PedidoPendenteDTO montarPedidoPendente(
            CriarPedidoMesaRequest request,
            boolean aguardandoPagamento,
            String pagamentoCorrelationId,
            List<MeioPagamentoRequest> meiosPagamento) {
        Mesa mesa = mesaRepository.buscarPorQrCodeToken(request.getMesaToken())
                .orElseThrow(() -> MesaNaoEncontradaException.porToken(request.getMesaToken()));

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

        return PedidoPendenteDTO.builder()
                .id(pedidoId)
                .tipo(PedidoPendenteDTO.TIPO_MESA)
                .mesaToken(request.getMesaToken())
                .mesaId(mesa.getId())
                .numeroMesa(mesa.getNumero())
                .clienteId(request.getClienteId())
                .nomeCliente(request.getNomeCliente())
                .itens(itens)
                .meiosPagamento(meiosPagamento)
                .observacoes(request.getObservacoes())
                .valorTotal(valorTotal)
                .dataHoraSolicitacao(LocalDateTime.now())
                .tempoEsperaSegundos(0)
                .aguardandoPagamento(aguardandoPagamento)
                .pagamentoCorrelationId(pagamentoCorrelationId)
                .build();
    }

    /**
     * Lista todos os pedidos pendentes na fila, ordenados por tempo de espera.
     * Remove automaticamente pedidos expirados.
     */
    @Transactional
    public List<PedidoPendenteDTO> listarPedidosPendentes() {
        pedidoPendenteRepository.removerExpirados(TEMPO_MAXIMO_FILA_MINUTOS);

        return pedidoPendenteRepository.listarPendentesPorTipo(PedidoPendenteDTO.TIPO_MESA).stream()
                .peek(PedidoPendenteDTO::atualizarTempoEspera)
                .sorted(Comparator.comparing(PedidoPendenteDTO::getDataHoraSolicitacao))
                .collect(Collectors.toList());
    }

    /**
     * Busca um pedido pendente pelo ID para fluxos publicos de status/pagamento.
     * Pedidos ocultos aguardando PIX continuam acessiveis aqui.
     */
    @Transactional(readOnly = true)
    public Optional<PedidoPendenteDTO> buscarPorId(String pedidoId) {
        return pedidoPendenteRepository.buscarPendentePorId(pedidoId)
                .map(pedido -> {
                    pedido.atualizarTempoEspera();
                    return pedido;
                });
    }

    /**
     * Busca um pedido visivel na fila operacional de funcionarios.
     */
    @Transactional(readOnly = true)
    public Optional<PedidoPendenteDTO> buscarVisivelPorId(String pedidoId) {
        return buscarPorId(pedidoId)
                .filter(pedido -> !pedido.isAguardandoPagamento());
    }

    /**
     * Busca e "remove" atomicamente um pedido da fila.
     * 
     * IMPORTANTE: Este método é thread-safe e garante que apenas um funcionário
     * consiga aceitar o mesmo pedido, evitando race conditions onde dois
     * funcionários poderiam aceitar o mesmo pedido simultaneamente.
     * 
     * Usa SELECT FOR UPDATE (lock pessimista) para garantir exclusividade.
     * 
     * NOTA: O registro NÃO é deletado do banco. Ao ser aceito, o campo
     * pedido_real_id
     * será preenchido pelo método registrarConversaoParaPedidoReal(), removendo-o
     * da listagem de pendentes (filtro: pedido_real_id IS NULL). Isso mantém
     * histórico para auditoria.
     * 
     * @param pedidoId ID do pedido a ser buscado e marcado para aceitação
     * @return Optional contendo o pedido se encontrado e ainda pendente, ou empty
     */
    @Transactional
    public Optional<PedidoPendenteDTO> buscarERemoverAtomicamente(String pedidoId) {
        Optional<PedidoPendenteDTO> pedidoOpt = pedidoPendenteRepository.buscarPendentePorIdComLock(pedidoId);

        if (pedidoOpt.isPresent()) {
            PedidoPendenteDTO pedido = pedidoOpt.get();
            pedido.atualizarTempoEspera();
            log.info("Pedido obtido para aceitação (com lock) - ID: {}, Mesa: {}",
                    pedidoId, pedido.getNumeroMesa());
            return Optional.of(pedido);
        }

        return Optional.empty();
    }

    /**
     * Remove um pedido da fila (quando aceito ou rejeitado).
     */
    @Transactional
    public PedidoPendenteDTO removerPedido(String pedidoId) {
        Optional<PedidoPendenteDTO> pedidoOpt = pedidoPendenteRepository.buscarPorId(pedidoId);
        if (pedidoOpt.isPresent()) {
            pedidoPendenteRepository.remover(pedidoId);
            log.info("Pedido removido da fila - ID: {}", pedidoId);
            return pedidoOpt.get();
        }
        return null;
    }

    /**
     * Registra o mapeamento entre um pedido pendente e o pedido real criado ao
     * aceitá-lo.
     */
    @Transactional
    public void registrarConversaoParaPedidoReal(String pedidoPendenteId, String pedidoRealId) {
        if (pedidoPendenteId != null && pedidoRealId != null) {
            pedidoPendenteRepository.marcarComoAceito(pedidoPendenteId, pedidoRealId);
            log.info("Mapeado pedido pendente {} -> pedido real {}", pedidoPendenteId, pedidoRealId);
        }
    }

    /**
     * Obtém o ID do pedido real a partir do ID pendente, se existir.
     */
    @Transactional(readOnly = true)
    public Optional<String> buscarPedidoRealPorPendente(String pedidoPendenteId) {
        return pedidoPendenteRepository.buscarPedidoRealPorPendente(pedidoPendenteId);
    }

    /**
     * Retorna a quantidade de pedidos na fila.
     */
    @Transactional
    public int quantidadePedidosPendentes() {
        pedidoPendenteRepository.removerExpirados(TEMPO_MAXIMO_FILA_MINUTOS);
        return (int) pedidoPendenteRepository.contarPendentesPorTipo(PedidoPendenteDTO.TIPO_MESA);
    }

    /**
     * Verifica se há pedidos pendentes na fila.
     */
    @Transactional(readOnly = true)
    public boolean existemPedidosPendentes() {
        return pedidoPendenteRepository.contarPendentesPorTipo(PedidoPendenteDTO.TIPO_MESA) > 0;
    }

    /**
     * Limpa toda a fila (para testes ou reset).
     * Remove todos os pedidos pendentes do banco.
     */
    @Transactional
    public void limparFila() {
        int removidos = pedidoPendenteRepository.removerExpirados(0);
        log.info("Fila de pedidos limpa - {} pedidos removidos", removidos);
    }
}