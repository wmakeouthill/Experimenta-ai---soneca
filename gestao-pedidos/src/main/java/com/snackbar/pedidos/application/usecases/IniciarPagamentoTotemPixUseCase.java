package com.snackbar.pedidos.application.usecases;

import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.IniciarPagamentoTotemPixRequest;
import com.snackbar.pedidos.application.dto.PixCobrancaCriadaDTO;
import com.snackbar.pedidos.application.ports.PagamentoTotemRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.ports.StonePixGatewayPort;
import com.snackbar.pedidos.application.ports.StonePixGatewayPort.CriarCobrancaPixCommand;
import com.snackbar.pedidos.domain.entities.MeioPagamentoTotem;
import com.snackbar.pedidos.domain.entities.PagamentoTotem;
import com.snackbar.pedidos.domain.valueobjects.DadosPix;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IniciarPagamentoTotemPixUseCase {

    private final PagamentoTotemRepositoryPort pagamentoRepository;
    private final PedidoRepositoryPort pedidoRepository;
    private final StonePixGatewayPort stonePixGateway;

    @Transactional
    public PixCobrancaCriadaDTO executar(IniciarPagamentoTotemPixRequest request) {
        var existente = pagamentoRepository.buscarPorCorrelationId(request.correlationId());
        if (existente.isPresent()) {
            return PixCobrancaCriadaDTO.de(existente.get());
        }

        var pedido = pedidoRepository.buscarPorId(request.pedidoId())
                .orElseThrow(() -> new ValidationException("Pedido nao encontrado"));

        long valorCentavos = pedido.getValorTotal().getAmount()
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        var pagamento = PagamentoTotem.iniciar(
                pedido.getId(),
                valorCentavos,
                MeioPagamentoTotem.PIX,
                request.correlationId());

        var cobranca = stonePixGateway.criarCobrancaDinamica(
                new CriarCobrancaPixCommand(pedido.getId(), request.correlationId(), valorCentavos));

        pagamento.marcarAguardandoPix(new DadosPix(
                cobranca.txid(),
                cobranca.qrCodePayload(),
                cobranca.qrCodeBase64(),
                cobranca.copiaECola(),
                cobranca.expiracaoEm()));

        var salvo = pagamentoRepository.salvar(pagamento);
        log.info("Pagamento PIX iniciado correlationId={} txid={}",
                salvo.getCorrelationId(), salvo.getPixTxid());
        return PixCobrancaCriadaDTO.de(salvo);
    }
}
