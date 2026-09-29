package com.snackbar.cardapio.application.usecases;

import com.snackbar.cardapio.application.ports.AdicionalRepositoryPort;
import com.snackbar.kernel.domain.exceptions.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExcluirAdicionalUseCase {

    private final AdicionalRepositoryPort adicionalRepository;

    public void executar(String id) {
        if (!adicionalRepository.existePorId(id)) {
            throw new RecursoNaoEncontradoException("Adicional não encontrado com ID: " + id);
        }

        adicionalRepository.excluir(id);
    }
}
