package com.snackbar.pedidos.application.usecases;

import org.springframework.stereotype.Service;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.PagamentoTotemDTO;
import com.snackbar.pedidos.application.ports.PagamentoTotemRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BuscarPagamentoTotemUseCase {

    private final PagamentoTotemRepositoryPort pagamentoRepository;

    public PagamentoTotemDTO executar(String correlationId) {
        validarObrigatorio(correlationId, "correlationId");

        return pagamentoRepository.buscarPorCorrelationId(correlationId)
                .map(PagamentoTotemDTO::de)
                .orElseThrow(() -> new ValidationException("Pagamento do totem nao encontrado"));
    }

    private static void validarObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ValidationException(campo + " e obrigatorio");
        }
    }
}
