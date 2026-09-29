package com.snackbar.pedidos.application.usecases;

import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;
import com.snackbar.pedidos.application.dto.ContaMesaComPixDTO;
import com.snackbar.pedidos.application.dto.ContaMesaDTO;
import com.snackbar.pedidos.application.dto.ContaMesaDTO.ItemContaDTO;
import com.snackbar.pedidos.application.dto.PixCobrancaCriadaDTO;
import com.snackbar.pedidos.application.ports.ConfiguracaoPagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.ContaMesaRepositoryPort;
import com.snackbar.pedidos.application.ports.MesaRepositoryPort;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort.CriarCobrancaPixCommand;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.ContaMesa;
import com.snackbar.pedidos.domain.entities.Mesa;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.valueobjects.DadosPix;
import com.snackbar.kernel.domain.exceptions.RecursoNaoEncontradoException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fecha a conta pos-paga de um cliente numa mesa: consolida os pedidos abertos
 * sem pagamento, cria a ContaMesa e gera uma unica cobranca PIX pelo total.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FecharContaMesaUseCase {

    private final PedidoRepositoryPort pedidoRepository;
    private final ContaMesaRepositoryPort contaRepository;
    private final PixGatewayPort pixGateway;
    private final PagamentoRepositoryPort pagamentoRepository;
    private final MesaRepositoryPort mesaRepository;
    private final ConfiguracaoPagamentoRepositoryPort configuracaoRepository;

    @Transactional
    public ContaMesaComPixDTO executar(String correlationId, String mesaToken, String clienteId) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new ValidationException("correlationId e obrigatorio");
        }
        if (clienteId == null || clienteId.isBlank()) {
            throw new ValidationException("Identificacao do cliente e obrigatoria");
        }

        ConfiguracaoPagamentoDTO config = configuracaoRepository.buscar();
        if (!config.pixMesaAtivo()) {
            throw new ValidationException("PIX na mesa nao esta habilitado");
        }
        if (config.modoMesa() != ModoPagamentoMesa.POS_PAGO) {
            throw new ValidationException("Conta pos-paga so esta disponivel no modo POS_PAGO");
        }

        Mesa mesa = mesaRepository.buscarPorQrCodeToken(mesaToken)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mesa nao encontrada"));

        // Idempotencia: conta ABERTA existente reaproveita o pagamento pendente.
        var contaAberta = contaRepository.buscarAbertaPorMesaECliente(mesa.getId(), clienteId);
        if (contaAberta.isPresent()) {
            ContaMesa conta = contaAberta.get();
            var pagamento = pagamentoRepository.buscarPorCorrelationId(conta.getCorrelationId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento da conta nao encontrado"));
            return new ContaMesaComPixDTO(toDTO(conta), PixCobrancaCriadaDTO.de(pagamento));
        }

        List<Pedido> pedidos = pedidoRepository.buscarAbertosPorMesaESemPagamento(mesa.getId(), clienteId);
        if (pedidos.isEmpty()) {
            throw new ValidationException("Nao ha pedidos em aberto para fechar a conta");
        }

        long valorCentavos = pedidos.stream()
                .mapToLong(p -> p.getValorTotal().getAmount()
                        .movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact())
                .sum();

        List<String> pedidoIds = pedidos.stream().map(Pedido::getId).toList();

        ContaMesa conta = ContaMesa.abrir(mesa.getId(), mesa.getNumero(), clienteId,
                pedidoIds, valorCentavos, correlationId);
        ContaMesa contaSalva;
        try {
            contaSalva = contaRepository.salvar(conta);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ValidationException("Conta ja esta sendo processada. Tente novamente.");
        }

        var pagamento = Pagamento.iniciarParaContaMesa(
                contaSalva.getId(),
                CanalPagamento.MESA,
                pixGateway.gateway(),
                valorCentavos,
                MeioPagamentoGateway.PIX,
                correlationId);

        var cobranca = pixGateway.criarCobrancaDinamica(
                new CriarCobrancaPixCommand(contaSalva.getId(), correlationId, valorCentavos));

        pagamento.marcarAguardandoPix(new DadosPix(
                cobranca.txid(),
                cobranca.qrCodePayload(),
                cobranca.qrCodeBase64(),
                cobranca.copiaECola(),
                cobranca.expiracaoEm()));

        var pagamentoSalvo = pagamentoRepository.salvar(pagamento);
        log.info("Conta de mesa {} fechada contaId={} valor={} txid={}",
                mesa.getNumero(), contaSalva.getId(), valorCentavos, pagamentoSalvo.getPixTxid());

        return new ContaMesaComPixDTO(toDTO(contaSalva), PixCobrancaCriadaDTO.de(pagamentoSalvo));
    }

    static ContaMesaDTO toDTO(ContaMesa conta) {
        List<ItemContaDTO> itens = conta.getPedidoIds().stream()
                .map(id -> new ItemContaDTO(id, null, 0L))
                .toList();
        return new ContaMesaDTO(conta.getId(), conta.getNumeroMesa(),
                conta.getStatus().name(), conta.getValorCentavos(), itens);
    }
}
