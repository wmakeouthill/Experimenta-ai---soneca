package com.snackbar.impressao.infrastructure.persistence;

import com.snackbar.impressao.application.ports.ConfiguracaoImpressoraRepositoryPort;
import com.snackbar.impressao.domain.entities.ConfiguracaoImpressoraEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConfiguracaoImpressoraRepositoryAdapter implements ConfiguracaoImpressoraRepositoryPort {
    
    private final ConfiguracaoImpressoraJpaRepository jpaRepository;
    
    @Override
    public ConfiguracaoImpressoraEntity salvar(ConfiguracaoImpressoraEntity config) {
        ConfiguracaoImpressoraJpaEntity entity = ConfiguracaoImpressoraMapper.paraEntity(config);
        ConfiguracaoImpressoraJpaEntity salva = jpaRepository.save(entity);
        return ConfiguracaoImpressoraMapper.paraDomain(salva);
    }
    
    @Override
    public Optional<ConfiguracaoImpressoraEntity> buscarAtiva() {
        return jpaRepository.findByAtivaTrue()
                .map(ConfiguracaoImpressoraMapper::paraDomain);
    }
    
    @Override
    public Optional<ConfiguracaoImpressoraEntity> buscarPorId(String id) {
        return jpaRepository.findById(id)
                .map(ConfiguracaoImpressoraMapper::paraDomain);
    }
    
    @Override
    public List<ConfiguracaoImpressoraEntity> buscarTodas() {
        return ConfiguracaoImpressoraMapper.paraDomainList(jpaRepository.findAll());
    }
}

