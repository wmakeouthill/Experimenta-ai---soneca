package com.snackbar.pedidos.application.usecases;

import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;
import com.snackbar.pedidos.application.dto.CriarPedidoMesaRequest;
import com.snackbar.pedidos.application.dto.PedidoMesaComPixDTO;
import com.snackbar.pedidos.application.dto.PedidoPendenteDTO;
import com.snackbar.pedidos.application.dto.PixCobrancaCriadaDTO;
import com.snackbar.pedidos.application.ports.ConfiguracaoPagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort.CriarCobrancaPixCommand;
import com.snackbar.pedidos.application.services.FilaPedidosMesaService;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.valueobjects.DadosPix;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cria um pedido-mesa no modo PRE_PAGO: o pedido nasce oculto e uma cobranca PIX
 * e gerada. Somente apos a aprovacao do PIX (webhook/job do Plano 1) o pedido e
 * liberado para a fila do funcionario.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CriarPedidoMesaComPixUseCase {

    private final FilaPedidosMesaService filaPedidosMesa;
    private final PixGatewayPort pixGateway;
    private final PagamentoRepositoryPort pagamentoRepository;
    private final ConfiguracaoPagamentoRepositoryPort configuracaoRepository;

    @Transactional
    public PedidoMesaComPixDTO executar(String correlationId, CriarPedidoMesaRequest request) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new ValidationException("correlationId e obrigatorio");
        }

        ConfiguracaoPagamentoDTO config = configuracaoRepository.buscar();
        if (!config.pixMesaAtivo()) {
            throw new ValidationException("PIX na mesa nao esta habilitado");
        }
        if (config.modoMesa() != ModoPagamentoMesa.PRE_PAGO) {
            throw new ValidationException("PIX pre-pago so esta disponivel no modo PRE_PAGO");
        }

        // Idempotencia: mesma correlationId retorna a cobranca existente.
        var existente = pagamentoRepository.buscarPorCorrelationId(correlationId);
        if (existente.isPresent()) {
            var pagamento = existente.get();
            PedidoPendenteDTO pendente = filaPedidosMesa.buscarPorId(pagamento.getPedidoPendenteId())
                    .orElseThrow(() -> new ValidationException("Pedido pendente nao encontrado"));
            return new PedidoMesaComPixDTO(pendente, PixCobrancaCriadaDTO.de(pagamento));
        }

        PedidoPendenteDTO pendente = filaPedidosMesa.adicionarPedidoAguardandoPagamento(request, correlationId);

        long valorCentavos = pendente.getValorTotal()
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        var pagamento = Pagamento.iniciarParaPedidoPendente(
                pendente.getId(),
                CanalPagamento.MESA,
                pixGateway.gateway(),
                valorCentavos,
                MeioPagamentoGateway.PIX,
                correlationId);

        var cobranca = pixGateway.criarCobrancaDinamica(
                new CriarCobrancaPixCommand(pendente.getId(), correlationId, valorCentavos));

        pagamento.marcarAguardandoPix(new DadosPix(
                cobranca.txid(),
                cobranca.qrCodePayload(),
                cobranca.qrCodeBase64(),
                cobranca.copiaECola(),
                cobranca.expiracaoEm()));

        var salvo = pagamentoRepository.salvar(pagamento);
        log.info("Pedido-mesa pre-pago criado pendenteId={} txid={} gateway={}",
                pendente.getId(), salvo.getPixTxid(), salvo.getGateway());

        return new PedidoMesaComPixDTO(pendente, PixCobrancaCriadaDTO.de(salvo));
    }
}
