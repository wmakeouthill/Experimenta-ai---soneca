package com.snackbar.pedidos.application.usecases;

import java.math.RoundingMode;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;
import com.snackbar.pedidos.application.dto.FecharContaComCartaoRequest;
import com.snackbar.pedidos.application.dto.ResultadoContaCartaoDTO;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort.DadosCartaoInput;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort.PagarCartaoCommand;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort.ResultadoPagamentoCartao;
import com.snackbar.pedidos.application.ports.ConfiguracaoPagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.ContaMesaRepositoryPort;
import com.snackbar.pedidos.application.ports.MesaRepositoryPort;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.services.AplicarPagamentoMesaAprovadoService;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.ContaMesa;
import com.snackbar.pedidos.domain.entities.Mesa;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.StatusPagamento;
import com.snackbar.pedidos.domain.valueobjects.DadosCartaoDigital;
import com.snackbar.kernel.domain.exceptions.RecursoNaoEncontradoException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FecharContaMesaComCartaoUseCase {

    private final PedidoRepositoryPort pedidoRepository;
    private final ContaMesaRepositoryPort contaRepository;
    private final CartaoDigitalGatewayPort cartaoGateway;
    private final PagamentoRepositoryPort pagamentoRepository;
    private final MesaRepositoryPort mesaRepository;
    private final ConfiguracaoPagamentoRepositoryPort configuracaoRepository;
    private final AplicarPagamentoMesaAprovadoService aplicarPagamentoAprovado;

    public ResultadoContaCartaoDTO executar(
            String correlationId, String clienteId, FecharContaComCartaoRequest request) {
        validarObrigatorio(correlationId, "correlationId");
        validarObrigatorio(clienteId, "Identificacao do cliente");

        ConfiguracaoPagamentoDTO config = configuracaoRepository.buscar();
        if (!config.cartaoMesaAtivo()) {
            throw new ValidationException("Cartao na mesa nao esta habilitado");
        }
        if (config.modoMesa() != ModoPagamentoMesa.POS_PAGO) {
            throw new ValidationException("Fechar conta com cartao so esta disponivel no modo POS_PAGO");
        }

        var existente = pagamentoRepository.buscarPorCorrelationId(correlationId);
        if (existente.isPresent()) {
            Pagamento pagamentoExistente = existente.get();
            ContaMesa conta = pagamentoExistente.getContaMesaId() != null
                    ? contaRepository.buscarPorId(pagamentoExistente.getContaMesaId()).orElse(null) : null;
            if (pagamentoExistente.getStatus() == StatusPagamento.APROVADO) {
                return new ResultadoContaCartaoDTO(true, null,
                        conta != null ? FecharContaMesaUseCase.toDTO(conta) : null);
            }
            String motivo = pagamentoExistente.getMotivo() != null
                    ? pagamentoExistente.getMotivo() : "Pagamento em processamento";
            return new ResultadoContaCartaoDTO(false, motivo,
                    conta != null ? FecharContaMesaUseCase.toDTO(conta) : null);
        }

        Mesa mesa = mesaRepository.buscarPorQrCodeToken(request.getMesaToken())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mesa nao encontrada"));

        if (contaRepository.buscarAbertaPorMesaECliente(mesa.getId(), clienteId).isPresent()) {
            throw new ValidationException("Ja existe uma conta em processamento para esta mesa");
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
        } catch (DataIntegrityViolationException exception) {
            throw new ValidationException("Conta ja esta sendo processada. Tente novamente.");
        }

        Pagamento pagamento = Pagamento.iniciarParaContaMesa(
                contaSalva.getId(),
                CanalPagamento.MESA,
                cartaoGateway.gateway(),
                valorCentavos,
                MeioPagamentoGateway.CARTAO_CREDITO,
                correlationId);
        pagamento = pagamentoRepository.salvarImediato(pagamento);

        var cartao = request.getCartao();
        ResultadoPagamentoCartao resultado = cartaoGateway.pagar(new PagarCartaoCommand(
                contaSalva.getId(),
                correlationId,
                valorCentavos,
                new DadosCartaoInput(cartao.getNumero(), cartao.getNomePortador(),
                        cartao.getValidadeMes(), cartao.getValidadeAno(), cartao.getCvv()),
                cartao.getParcelas()));

        if (resultado.aprovado()) {
            pagamento.aprovarCartaoDigital(new DadosCartaoDigital(
                    resultado.gatewayPaymentId(), resultado.bandeira(), resultado.codigoAutorizacao()));
            aplicarPagamentoAprovado.aplicar(pagamento, MeioPagamento.CARTAO_CREDITO);
            pagamentoRepository.salvar(pagamento);
            contaSalva.pagar();
            log.info("Conta {} paga com cartao paymentId={}", contaSalva.getId(), resultado.gatewayPaymentId());
            return new ResultadoContaCartaoDTO(true, null, FecharContaMesaUseCase.toDTO(contaSalva));
        }

        pagamento.negar(resultado.motivo());
        pagamentoRepository.salvar(pagamento);
        contaSalva.cancelar();
        ContaMesa contaCancelada = contaRepository.salvar(contaSalva);
        log.info("Conta {} cancelada por cartao recusado motivo={}", contaSalva.getId(), resultado.motivo());
        return new ResultadoContaCartaoDTO(false, resultado.motivo(),
                FecharContaMesaUseCase.toDTO(contaCancelada));
    }

    private static void validarObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ValidationException(campo + " e obrigatorio");
        }
    }
}
