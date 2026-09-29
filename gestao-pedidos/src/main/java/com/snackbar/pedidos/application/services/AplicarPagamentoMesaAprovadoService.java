package com.snackbar.pedidos.application.services;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.ports.ContaMesaRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoPendenteRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.ContaMesa;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoPedido;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.kernel.domain.exceptions.RecursoNaoEncontradoException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aplica os efeitos de um pagamento aprovado conforme a referencia do pagamento.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AplicarPagamentoMesaAprovadoService {

    private final PedidoRepositoryPort pedidoRepository;
    private final PedidoPendenteRepositoryPort pedidoPendenteRepository;
    private final ContaMesaRepositoryPort contaMesaRepository;

    @Transactional
    public void aplicar(Pagamento pagamento, MeioPagamento meioPedido) {
        if (pagamento.getCanal() == CanalPagamento.TOTEM) {
            carimbarPedido(pagamento.getPedidoId(), pagamento.getValorCentavos(), meioPedido);
        } else if (pagamento.getPedidoPendenteId() != null) {
            pedidoPendenteRepository.liberarPagamento(pagamento.getPedidoPendenteId());
            log.info("Pedido pendente {} liberado apos pagamento aprovado", pagamento.getPedidoPendenteId());
        } else if (pagamento.getContaMesaId() != null) {
            pagarConta(pagamento.getContaMesaId(), meioPedido);
        } else {
            throw new ValidationException("Pagamento de mesa sem referencia valida");
        }
    }

    private void carimbarPedido(String pedidoId, long valorCentavos, MeioPagamento meioPedido) {
        Pedido pedido = pedidoRepository.buscarPorId(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido nao encontrado"));

        if (pedido.getMeiosPagamento() != null && !pedido.getMeiosPagamento().isEmpty()) {
            throw new ValidationException("Pedido ja possui pagamento registrado");
        }

        pedido.adicionarMeioPagamento(MeioPagamentoPedido.criar(
                meioPedido, Preco.of(BigDecimal.valueOf(valorCentavos, 2))));
        pedidoRepository.salvar(pedido);
    }

    private void pagarConta(String contaMesaId, MeioPagamento meioPedido) {
        ContaMesa conta = contaMesaRepository.buscarPorId(contaMesaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Conta de mesa nao encontrada: " + contaMesaId));

        conta.pagar();
        contaMesaRepository.salvar(conta);

        for (String pedidoId : conta.getPedidoIds()) {
            Pedido pedido = pedidoRepository.buscarPorId(pedidoId).orElse(null);
            if (pedido == null) {
                log.warn("Pedido {} da conta {} nao encontrado ao registrar pagamento", pedidoId, contaMesaId);
                continue;
            }
            if (pedido.getMeiosPagamento() != null && !pedido.getMeiosPagamento().isEmpty()) {
                continue;
            }
            pedido.adicionarMeioPagamento(MeioPagamentoPedido.criar(
                    meioPedido, Preco.of(pedido.getValorTotal().getAmount())));
            pedidoRepository.salvar(pedido);
        }
        log.info("Conta de mesa {} marcada como PAGA e pedidos carimbados", contaMesaId);
    }
}
