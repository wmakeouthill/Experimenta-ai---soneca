package com.snackbar.pedidos.application.usecases;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.CancelarPagamentoTotemRequest;
import com.snackbar.pedidos.application.dto.PagamentoTotemDTO;
import com.snackbar.pedidos.application.ports.PagamentoTotemRepositoryPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CancelarPagamentoTotemUseCase {

    private final PagamentoTotemRepositoryPort pagamentoRepository;

    @Transactional
    public PagamentoTotemDTO executar(CancelarPagamentoTotemRequest request) {
        var pagamento = pagamentoRepository.buscarPorCorrelationId(request.correlationId())
                .orElseThrow(() -> new ValidationException("Pagamento do totem nao encontrado"));

        if (!pagamento.estaFinalizado()) {
            pagamento.cancelar(request.motivo());
            pagamento = pagamentoRepository.salvar(pagamento);
        }

        log.info("Pagamento do totem cancelado correlationId={}", pagamento.getCorrelationId());
        return PagamentoTotemDTO.de(pagamento);
    }
}
