package com.snackbar.pedidos.application.usecases;

import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;
import com.snackbar.pedidos.application.dto.PagarPedidoMesaComCartaoRequest;
import com.snackbar.pedidos.application.dto.PedidoPendenteDTO;
import com.snackbar.pedidos.application.dto.ResultadoPagamentoCartaoDTO;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort.DadosCartaoInput;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort.PagarCartaoCommand;
import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort.ResultadoPagamentoCartao;
import com.snackbar.pedidos.application.ports.ConfiguracaoPagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoPendenteRepositoryPort;
import com.snackbar.pedidos.application.services.AplicarPagamentoMesaAprovadoService;
import com.snackbar.pedidos.application.services.FilaPedidosMesaService;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.pedidos.domain.entities.MeioPagamentoGateway;
import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.entities.StatusPagamento;
import com.snackbar.pedidos.domain.valueobjects.DadosCartaoDigital;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PagarPedidoMesaComCartaoUseCase {

    private final FilaPedidosMesaService filaPedidosMesa;
    private final CartaoDigitalGatewayPort cartaoGateway;
    private final PagamentoRepositoryPort pagamentoRepository;
    private final PedidoPendenteRepositoryPort pedidoPendenteRepository;
    private final ConfiguracaoPagamentoRepositoryPort configuracaoRepository;
    private final AplicarPagamentoMesaAprovadoService aplicarPagamentoAprovado;

    public ResultadoPagamentoCartaoDTO executar(String correlationId, PagarPedidoMesaComCartaoRequest request) {
        validarObrigatorio(correlationId, "correlationId");

        ConfiguracaoPagamentoDTO config = configuracaoRepository.buscar();
        if (!config.cartaoMesaAtivo()) {
            throw new ValidationException("Cartao na mesa nao esta habilitado");
        }
        if (config.modoMesa() != ModoPagamentoMesa.PRE_PAGO) {
            throw new ValidationException("Cartao pre-pago so esta disponivel no modo PRE_PAGO");
        }

        var existente = pagamentoRepository.buscarPorCorrelationId(correlationId);
        if (existente.isPresent()) {
            Pagamento pagamento = existente.get();
            if (pagamento.getStatus() == StatusPagamento.APROVADO) {
                PedidoPendenteDTO pendente = filaPedidosMesa.buscarPorId(pagamento.getPedidoPendenteId()).orElse(null);
                return new ResultadoPagamentoCartaoDTO(true, null, pendente);
            }
            String motivo = pagamento.getMotivo() != null ? pagamento.getMotivo() : "Pagamento em processamento";
            return new ResultadoPagamentoCartaoDTO(false, motivo, null);
        }

        PedidoPendenteDTO pendente = filaPedidosMesa.adicionarPedidoAguardandoPagamento(
                request.getPedido(), correlationId, MeioPagamento.CARTAO_CREDITO);
        long valorCentavos = pendente.getValorTotal()
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        Pagamento pagamento = Pagamento.iniciarParaPedidoPendente(
                pendente.getId(),
                CanalPagamento.MESA,
                cartaoGateway.gateway(),
                valorCentavos,
                MeioPagamentoGateway.CARTAO_CREDITO,
                correlationId);
        pagamento = pagamentoRepository.salvarImediato(pagamento);

        var cartao = request.getCartao();
        ResultadoPagamentoCartao resultado = cartaoGateway.pagar(new PagarCartaoCommand(
                pendente.getId(),
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
            pendente.setAguardandoPagamento(false);
            log.info("Cartao aprovado pendenteId={} paymentId={}", pendente.getId(), resultado.gatewayPaymentId());
            return new ResultadoPagamentoCartaoDTO(true, null, pendente);
        }

        pagamento.negar(resultado.motivo());
        pagamentoRepository.salvar(pagamento);
        pedidoPendenteRepository.remover(pendente.getId());
        log.info("Cartao recusado pendenteId={} motivo={}", pendente.getId(), resultado.motivo());
        return new ResultadoPagamentoCartaoDTO(false, resultado.motivo(), null);
    }

    private static void validarObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ValidationException(campo + " e obrigatorio");
        }
    }
}
