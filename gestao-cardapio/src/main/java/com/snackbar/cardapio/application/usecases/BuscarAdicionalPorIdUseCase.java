package com.snackbar.cardapio.application.usecases;

import com.snackbar.cardapio.application.dto.AdicionalDTO;
import com.snackbar.cardapio.application.ports.AdicionalRepositoryPort;
import com.snackbar.kernel.domain.exceptions.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuscarAdicionalPorIdUseCase {

    private final AdicionalRepositoryPort adicionalRepository;

    public AdicionalDTO executar(String id) {
        return adicionalRepository.buscarPorId(id)
                .map(AdicionalDTO::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Adicional não encontrado com ID: " + id));
    }
}
