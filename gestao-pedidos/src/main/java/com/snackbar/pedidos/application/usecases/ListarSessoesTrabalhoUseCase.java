package com.snackbar.pedidos.application.usecases;

import com.snackbar.pedidos.application.dto.SessaoTrabalhoDTO;
import com.snackbar.pedidos.application.ports.ObterNomeUsuarioPort;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import com.snackbar.pedidos.domain.entities.SessaoTrabalho;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ListarSessoesTrabalhoUseCase {

    private final SessaoTrabalhoRepositoryPort repository;
    private final ObterNomeUsuarioPort obterNomeUsuarioPort;

    public List<SessaoTrabalhoDTO> executar(LocalDate dataInicio) {
        List<SessaoTrabalho> sessoes = dataInicio != null
            ? repository.buscarPorDataInicio(dataInicio)
            : repository.buscarTodas();

        List<String> usuarioIds = sessoes.stream()
            .map(SessaoTrabalho::getUsuarioId)
            .distinct()
            .toList();
        Map<String, String> nomesPorId = obterNomeUsuarioPort.obterNomesPorIds(usuarioIds);

        return sessoes.stream()
            .map(sessao -> {
                SessaoTrabalhoDTO dto = SessaoTrabalhoDTO.de(sessao);
                dto.setUsuarioNome(nomesPorId.getOrDefault(sessao.getUsuarioId(), sessao.getUsuarioId()));
                return dto;
            })
            .toList();
    }
}

