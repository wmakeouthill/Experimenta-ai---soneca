package com.snackbar.pedidos.application.usecases;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.SessaoTrabalhoDTO;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import com.snackbar.pedidos.domain.entities.SessaoTrabalho;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RetomarSessaoTrabalhoUseCase {
    
    private final SessaoTrabalhoRepositoryPort repository;
    
    public SessaoTrabalhoDTO executar(String sessaoId) {
        SessaoTrabalho sessao = buscarSessao(sessaoId);
        sessao.retomar();
        SessaoTrabalho sessaoSalva = repository.salvar(sessao);
        return SessaoTrabalhoDTO.de(sessaoSalva);
    }
    
    private SessaoTrabalho buscarSessao(String sessaoId) {
        Optional<SessaoTrabalho> sessao = repository.buscarPorId(sessaoId);
        if (sessao.isEmpty()) {
            throw new ValidationException("Sessão não encontrada: " + sessaoId);
        }
        return sessao.get();
    }
}

