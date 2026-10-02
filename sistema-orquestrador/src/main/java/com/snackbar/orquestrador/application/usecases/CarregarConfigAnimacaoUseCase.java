package com.snackbar.orquestrador.application.usecases;

import com.snackbar.orquestrador.application.dto.ConfigAnimacaoDTO;
import com.snackbar.orquestrador.application.ports.ConfigAnimacaoRepositoryPort;
import com.snackbar.orquestrador.application.services.ReelsJsonCodec;
import com.snackbar.orquestrador.domain.entities.ConfigAnimacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CarregarConfigAnimacaoUseCase {

    private final ConfigAnimacaoRepositoryPort repository;
    private final ReelsJsonCodec reelsJsonCodec;

    public ConfigAnimacaoDTO executar() {
        return repository.buscar()
                .map(this::paraDto)
                .orElseGet(this::criarConfigPadrao);
    }

    private ConfigAnimacaoDTO paraDto(ConfigAnimacao config) {
        ConfigAnimacaoDTO dto = ConfigAnimacaoDTO.de(config);
        dto.setReels(reelsJsonCodec.ler(config.getReelsJson()));
        return dto;
    }

    private ConfigAnimacaoDTO criarConfigPadrao() {
        ConfigAnimacao configPadrao = ConfigAnimacao.criar(true, 30, 6);
        if (configPadrao == null) {
            throw new IllegalStateException("Configuração padrão não pôde ser criada");
        }
        ConfigAnimacao salva = repository.salvar(configPadrao);
        return paraDto(salva);
    }
}
