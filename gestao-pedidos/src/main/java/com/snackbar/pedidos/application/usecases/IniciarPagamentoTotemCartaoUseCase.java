package com.snackbar.pedidos.application.usecases;

import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.IniciarPagamentoTotemCartaoRequest;
import com.snackbar.pedidos.application.dto.PagamentoTotemDTO;
import com.snackbar.pedidos.application.ports.PagamentoTotemRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.PagamentoTotem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IniciarPagamentoTotemCartaoUseCase {

    private final PagamentoTotemRepositoryPort pagamentoRepository;
    private final PedidoRepositoryPort pedidoRepository;

    @Transactional
    public PagamentoTotemDTO executar(IniciarPagamentoTotemCartaoRequest request) {
        if (!request.meioPagamento().isCartao()) {
            throw new ValidationException("meio de pagamento deve ser cartao");
        }

        var existente = pagamentoRepository.buscarPorCorrelationId(request.correlationId());
        if (existente.isPresent()) {
            return PagamentoTotemDTO.de(existente.get());
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
                request.meioPagamento(),
                request.correlationId());
        pagamento.marcarAguardandoTef();

        var salvo = pagamentoRepository.salvar(pagamento);
        log.info("Pagamento TEF iniciado correlationId={} pedidoId={}",
                salvo.getCorrelationId(), salvo.getPedidoId());
        return PagamentoTotemDTO.de(salvo);
    }
}
