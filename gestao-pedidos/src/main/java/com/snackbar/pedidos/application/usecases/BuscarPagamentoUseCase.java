package com.snackbar.pedidos.application.usecases;

import org.springframework.stereotype.Service;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.PagamentoDTO;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BuscarPagamentoUseCase {

    private final PagamentoRepositoryPort pagamentoRepository;

    public PagamentoDTO executar(String correlationId) {
        validarObrigatorio(correlationId, "correlationId");

        return pagamentoRepository.buscarPorCorrelationId(correlationId)
                .map(PagamentoDTO::de)
                .orElseThrow(() -> new ValidationException("Pagamento nao encontrado"));
    }

    private static void validarObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ValidationException(campo + " e obrigatorio");
        }
    }
}
