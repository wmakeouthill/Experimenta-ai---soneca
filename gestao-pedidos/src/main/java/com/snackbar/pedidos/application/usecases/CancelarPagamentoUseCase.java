package com.snackbar.pedidos.application.usecases;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.CancelarPagamentoRequest;
import com.snackbar.pedidos.application.dto.PagamentoDTO;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CancelarPagamentoUseCase {

    private final PagamentoRepositoryPort pagamentoRepository;

    @Transactional
    public PagamentoDTO executar(CancelarPagamentoRequest request) {
        var pagamento = pagamentoRepository.buscarPorCorrelationId(request.correlationId())
                .orElseThrow(() -> new ValidationException("Pagamento nao encontrado"));

        if (!pagamento.estaFinalizado()) {
            pagamento.cancelar(request.motivo());
            pagamento = pagamentoRepository.salvar(pagamento);
        }

        log.info("Pagamento cancelado correlationId={}", pagamento.getCorrelationId());
        return PagamentoDTO.de(pagamento);
    }
}
