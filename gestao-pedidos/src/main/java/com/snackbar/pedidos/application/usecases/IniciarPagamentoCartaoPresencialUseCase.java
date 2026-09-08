package com.snackbar.pedidos.application.usecases;

import java.math.RoundingMode;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.IniciarPagamentoCartaoPresencialRequest;
import com.snackbar.pedidos.application.dto.PagamentoDTO;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.CanalPagamento;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.Pagamento;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IniciarPagamentoCartaoPresencialUseCase {

    private final PagamentoRepositoryPort pagamentoRepository;
    private final PedidoRepositoryPort pedidoRepository;

    @Transactional
    public PagamentoDTO executar(IniciarPagamentoCartaoPresencialRequest request) {
        if (!request.meioPagamento().isCartao()) {
            throw new ValidationException("meio de pagamento deve ser cartao");
        }

        var existente = pagamentoRepository.buscarPorCorrelationId(request.correlationId());
        if (existente.isPresent()) {
            return PagamentoDTO.de(existente.get());
        }

        var pedido = pedidoRepository.buscarPorId(request.pedidoId())
                .orElseThrow(() -> new ValidationException("Pedido nao encontrado"));

        long valorCentavos = pedido.getValorTotal().getAmount()
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        // Cartao presencial (TEF/maquininha): a integracao real depende da
        // definicao do equipamento (Fase 0). Ate la o fluxo e simulado.
        var pagamento = Pagamento.iniciarParaPedido(
                pedido.getId(),
                CanalPagamento.TOTEM,
                GatewayPagamento.SIMULADO,
                valorCentavos,
                request.meioPagamento(),
                request.correlationId());
        pagamento.marcarAguardandoTef();

        // A checagem acima nao fecha a corrida entre duas requisicoes com o mesmo
        // correlationId (duplo toque no totem). Quem perde bate no unique de correlation_id
        // e recebe orientacao de repetir: no retry o buscarPorCorrelationId ja encontra.
        Pagamento salvo;
        try {
            // salvarImediato (saveAndFlush) e o que faz o unique estourar aqui dentro; com
            // salvar comum o INSERT so vai no commit, depois deste catch.
            salvo = pagamentoRepository.salvarImediato(pagamento);
        } catch (DataIntegrityViolationException exception) {
            throw new ValidationException("Pagamento ja esta sendo processado. Tente novamente.");
        }

        log.info("Pagamento TEF iniciado correlationId={} pedidoId={}",
                salvo.getCorrelationId(), salvo.getPedidoId());
        return PagamentoDTO.de(salvo);
    }
}
