package com.snackbar.pedidos.application.usecases;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.ConfirmarPagamentoCartaoPresencialRequest;
import com.snackbar.pedidos.application.dto.PagamentoDTO;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.MeioPagamentoPedido;
import com.snackbar.pedidos.domain.valueobjects.DadosTef;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmarPagamentoCartaoPresencialUseCase {

    private final PagamentoRepositoryPort pagamentoRepository;
    private final PedidoRepositoryPort pedidoRepository;

    @Transactional
    public PagamentoDTO executar(ConfirmarPagamentoCartaoPresencialRequest request) {
        var pagamento = pagamentoRepository.buscarPorCorrelationId(request.correlationId())
                .orElseThrow(() -> new ValidationException("Pagamento nao encontrado"));

        if (pagamento.estaFinalizado()) {
            return PagamentoDTO.de(pagamento);
        }

        if (Boolean.TRUE.equals(request.aprovado())) {
            pagamento.aprovarTef(new DadosTef(
                    request.nsuTef(),
                    request.bandeira(),
                    request.codigoAutorizacao(),
                    request.codigoAdquirente(),
                    request.comprovanteCliente()));
            registrarMeioPagamentoNoPedido(pagamento.getPedidoId(), pagamento.getMeioPagamento(),
                    pagamento.getValorCentavos());
        } else {
            pagamento.negar(request.motivo());
        }

        var salvo = pagamentoRepository.salvar(pagamento);
        log.info("Pagamento TEF confirmado correlationId={} status={}",
                salvo.getCorrelationId(), salvo.getStatus());
        return PagamentoDTO.de(salvo);
    }

    private void registrarMeioPagamentoNoPedido(String pedidoId, MeioPagamentoGateway meioGateway,
            long valorCentavos) {
        var pedido = pedidoRepository.buscarPorId(pedidoId)
                .orElseThrow(() -> new ValidationException("Pedido nao encontrado"));

        if (pedido.getMeiosPagamento() != null && !pedido.getMeiosPagamento().isEmpty()) {
            throw new ValidationException("Pedido ja possui pagamento registrado");
        }

        pedido.adicionarMeioPagamento(MeioPagamentoPedido.criar(
                mapearMeioPagamento(meioGateway),
                Preco.of(BigDecimal.valueOf(valorCentavos, 2))));
        pedidoRepository.salvar(pedido);
    }

    private MeioPagamento mapearMeioPagamento(MeioPagamentoGateway meioGateway) {
        return switch (meioGateway) {
            case CARTAO_CREDITO -> MeioPagamento.CARTAO_CREDITO;
            case CARTAO_DEBITO -> MeioPagamento.CARTAO_DEBITO;
            case CARTAO_VOUCHER -> MeioPagamento.VALE_REFEICAO;
            case PIX -> throw new ValidationException("PIX nao deve ser confirmado como cartao");
        };
    }
}
