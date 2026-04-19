package com.snackbar.pedidos.application.usecases;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.PagamentoTotemDTO;
import com.snackbar.pedidos.application.ports.PagamentoTotemRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoPedido;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmarPagamentoTotemPixUseCase {

    private final PagamentoTotemRepositoryPort pagamentoRepository;
    private final PedidoRepositoryPort pedidoRepository;

    @Transactional
    public PagamentoTotemDTO executar(String txid, String endToEndId) {
        validarObrigatorio(txid, "txid");
        validarObrigatorio(endToEndId, "endToEndId");

        var pagamento = pagamentoRepository.buscarPorTxidPix(txid)
                .orElseThrow(() -> new ValidationException("Pagamento PIX nao encontrado"));

        if (pagamento.estaFinalizado()) {
            return PagamentoTotemDTO.de(pagamento);
        }

        pagamento.aprovarPix(endToEndId);
        registrarPixNoPedido(pagamento.getPedidoId(), pagamento.getValorCentavos());

        var salvo = pagamentoRepository.salvar(pagamento);
        log.info("Pagamento PIX confirmado correlationId={} txid={}",
                salvo.getCorrelationId(), salvo.getPixTxid());
        return PagamentoTotemDTO.de(salvo);
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

    private static void validarObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ValidationException(campo + " e obrigatorio");
        }
    }
}
