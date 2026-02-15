package com.snackbar.pedidos.application.usecases;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.SessaoTrabalhoDTO;
import com.snackbar.pedidos.application.ports.ObterNomeUsuarioPort;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import com.snackbar.pedidos.domain.entities.SessaoTrabalho;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PausarSessaoTrabalhoUseCase {

    private final SessaoTrabalhoRepositoryPort repository;
    private final ObterNomeUsuarioPort obterNomeUsuarioPort;

    @SuppressWarnings("null") // repository.salvar() nunca retorna null, .get() nunca retorna null porque validamos antes
    public SessaoTrabalhoDTO executar(@NonNull String sessaoId) {
        SessaoTrabalho sessao = buscarSessao(sessaoId);
        sessao.pausar();
        SessaoTrabalho sessaoSalva = repository.salvar(sessao);
        SessaoTrabalhoDTO dto = SessaoTrabalhoDTO.de(sessaoSalva);
        String nome = obterNomeUsuarioPort.obterNomesPorIds(Collections.singleton(sessaoSalva.getUsuarioId()))
            .getOrDefault(sessaoSalva.getUsuarioId(), sessaoSalva.getUsuarioId());
        dto.setUsuarioNome(nome);
        return dto;
    }
    
    private SessaoTrabalho buscarSessao(@NonNull String sessaoId) {
        Optional<SessaoTrabalho> sessao = repository.buscarPorId(sessaoId);
        if (sessao.isEmpty()) {
            throw new ValidationException("Sessão não encontrada: " + sessaoId);
        }
        return sessao.get();
    }
}

