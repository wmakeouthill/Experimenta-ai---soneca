package com.snackbar.pedidos.application.usecases;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.PagamentoDTO;
import com.snackbar.pedidos.application.ports.ContaMesaRepositoryPort;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoPendenteRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.ContaMesa;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoPedido;
import com.snackbar.pedidos.domain.entities.Pedido;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmarPagamentoPixUseCase {

    private final PagamentoRepositoryPort pagamentoRepository;
    private final PedidoRepositoryPort pedidoRepository;
    private final PedidoPendenteRepositoryPort pedidoPendenteRepository;
    private final ContaMesaRepositoryPort contaMesaRepository;

    @Transactional
    public PagamentoDTO executar(String txid, String endToEndId) {
        validarObrigatorio(txid, "txid");
        validarObrigatorio(endToEndId, "endToEndId");

        var pagamento = pagamentoRepository.buscarPorTxidPix(txid)
                .orElseThrow(() -> new ValidationException("Pagamento PIX nao encontrado"));

        if (pagamento.estaFinalizado()) {
            return PagamentoDTO.de(pagamento);
        }

        pagamento.aprovarPix(endToEndId);

        if (pagamento.getCanal() == CanalPagamento.TOTEM) {
            registrarPixNoPedido(pagamento.getPedidoId(), pagamento.getValorCentavos());
        } else if (pagamento.getPedidoPendenteId() != null) {
            liberarPedidoPendente(pagamento.getPedidoPendenteId());
        } else if (pagamento.getContaMesaId() != null) {
            pagarContaMesa(pagamento.getContaMesaId());
        } else {
            throw new ValidationException("Pagamento de mesa sem referencia valida");
        }

        var salvo = pagamentoRepository.salvar(pagamento);
        log.info("Pagamento PIX confirmado correlationId={} txid={}",
                salvo.getCorrelationId(), salvo.getPixTxid());
        return PagamentoDTO.de(salvo);
    }

    private void registrarPixNoPedido(String pedidoId, long valorCentavos) {
        var pedido = pedidoRepository.buscarPorId(pedidoId)
                .orElseThrow(() -> new ValidationException("Pedido nao encontrado"));

        if (pedido.getMeiosPagamento() != null && !pedido.getMeiosPagamento().isEmpty()) {
            throw new ValidationException("Pedido ja possui pagamento registrado");
        }

        pedido.adicionarMeioPagamento(MeioPagamentoPedido.criar(
                MeioPagamento.PIX,
                Preco.of(BigDecimal.valueOf(valorCentavos, 2))));
        pedidoRepository.salvar(pedido);
    }

    private void pagarContaMesa(String contaMesaId) {
        ContaMesa conta = contaMesaRepository.buscarPorId(contaMesaId)
                .orElseThrow(() -> new ValidationException(
                        "Conta de mesa nao encontrada: " + contaMesaId));

        conta.pagar();
        contaMesaRepository.salvar(conta);

        for (String pedidoId : conta.getPedidoIds()) {
            Pedido pedido = pedidoRepository.buscarPorId(pedidoId).orElse(null);
            if (pedido == null) {
                log.warn("Pedido {} da conta {} nao encontrado ao registrar PIX", pedidoId, contaMesaId);
                continue;
            }
            if (pedido.getMeiosPagamento() != null && !pedido.getMeiosPagamento().isEmpty()) {
                continue; // ja carimbado (idempotencia)
            }
            pedido.adicionarMeioPagamento(
                    MeioPagamentoPedido.criar(MeioPagamento.PIX, Preco.of(pedido.getValorTotal().getAmount())));
            pedidoRepository.salvar(pedido);
        }
        log.info("Conta de mesa {} marcada como PAGA e pedidos carimbados com PIX", contaMesaId);
    }

    private void liberarPedidoPendente(String pedidoPendenteId) {
        pedidoPendenteRepository.liberarPagamento(pedidoPendenteId);
        log.info("Pedido pendente {} liberado para a fila apos PIX aprovado", pedidoPendenteId);
    }

    private static void validarObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ValidationException(campo + " e obrigatorio");
        }
    }
}
