package com.snackbar.orquestrador.infrastructure.persistence;

import com.snackbar.orquestrador.application.ports.ConfigAnimacaoRepositoryPort;
import com.snackbar.orquestrador.domain.entities.ConfigAnimacao;
import com.snackbar.orquestrador.infrastructure.mappers.ConfigAnimacaoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConfigAnimacaoRepositoryAdapter implements ConfigAnimacaoRepositoryPort {

    private final ConfigAnimacaoJpaRepository jpaRepository;
    private final ConfigAnimacaoMapper mapper;

    @Override
    @SuppressWarnings("null")
    public ConfigAnimacao salvar(@NonNull ConfigAnimacao config) {
        ConfigAnimacaoEntity entity = mapper.paraEntity(config);
        ConfigAnimacaoEntity salva = jpaRepository.save(entity);
        return mapper.paraDomain(salva);
    }

    @Override
    public Optional<ConfigAnimacao> buscar() {
        return jpaRepository.findFirstByOrderByCreatedAtAsc()
                .map(mapper::paraDomain);
    }
}

