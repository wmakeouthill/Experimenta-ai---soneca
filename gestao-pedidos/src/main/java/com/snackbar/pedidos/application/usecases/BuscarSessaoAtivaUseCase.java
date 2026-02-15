package com.snackbar.pedidos.application.usecases;

import com.snackbar.pedidos.application.dto.SessaoTrabalhoDTO;
import com.snackbar.pedidos.application.ports.ObterNomeUsuarioPort;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import com.snackbar.pedidos.domain.entities.SessaoTrabalho;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BuscarSessaoAtivaUseCase {

    private final SessaoTrabalhoRepositoryPort repository;
    private final ObterNomeUsuarioPort obterNomeUsuarioPort;

    public Optional<SessaoTrabalhoDTO> executar() {
        return repository.buscarSessaoAtiva()
            .map(this::enriquecerComNomeUsuario);
    }

    private SessaoTrabalhoDTO enriquecerComNomeUsuario(SessaoTrabalho sessao) {
        SessaoTrabalhoDTO dto = SessaoTrabalhoDTO.de(sessao);
        String nome = obterNomeUsuarioPort.obterNomesPorIds(Collections.singleton(sessao.getUsuarioId()))
            .getOrDefault(sessao.getUsuarioId(), sessao.getUsuarioId());
        dto.setUsuarioNome(nome);
        return dto;
    }
}

