package com.snackbar.pedidos.application.usecases;

import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.pedidos.application.dto.IniciarPagamentoPixRequest;
import com.snackbar.pedidos.application.dto.PixCobrancaCriadaDTO;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort.CriarCobrancaPixCommand;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.valueobjects.DadosPix;
import com.snackbar.kernel.domain.exceptions.RecursoNaoEncontradoException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IniciarPagamentoPixUseCase {

    private final PagamentoRepositoryPort pagamentoRepository;
    private final PedidoRepositoryPort pedidoRepository;
    private final PixGatewayPort pixGateway;

    @Transactional
    public PixCobrancaCriadaDTO executar(IniciarPagamentoPixRequest request) {
        var existente = pagamentoRepository.buscarPorCorrelationId(request.correlationId());
        if (existente.isPresent()) {
            return PixCobrancaCriadaDTO.de(existente.get());
        }

        var pedido = pedidoRepository.buscarPorId(request.pedidoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido nao encontrado"));

        // Regeneracao de QR: expira cobranca pendente anterior do mesmo pedido.
        pagamentoRepository.buscarAguardandoPixPorPedidoId(pedido.getId())
                .ifPresent(anterior -> {
                    anterior.expirar();
                    pagamentoRepository.salvar(anterior);
                    log.info("Pagamento PIX anterior expirado para regeneracao correlationId={}",
                            anterior.getCorrelationId());
                });

        long valorCentavos = pedido.getValorTotal().getAmount()
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        var pagamento = Pagamento.iniciarParaPedido(
                pedido.getId(),
                CanalPagamento.TOTEM,
                pixGateway.gateway(),
                valorCentavos,
                MeioPagamentoGateway.PIX,
                request.correlationId());

        var cobranca = pixGateway.criarCobrancaDinamica(
                new CriarCobrancaPixCommand(pedido.getId(), request.correlationId(), valorCentavos));

        pagamento.marcarAguardandoPix(new DadosPix(
                cobranca.txid(),
                cobranca.qrCodePayload(),
                cobranca.qrCodeBase64(),
                cobranca.copiaECola(),
                cobranca.expiracaoEm()));

        var salvo = pagamentoRepository.salvar(pagamento);
        log.info("Pagamento PIX iniciado correlationId={} txid={} gateway={}",
                salvo.getCorrelationId(), salvo.getPixTxid(), salvo.getGateway());
        return PixCobrancaCriadaDTO.de(salvo);
    }
}
