package com.snackbar.impressao.infrastructure.persistence;

import com.snackbar.impressao.domain.entities.ConfiguracaoImpressoraEntity;
import com.snackbar.impressao.domain.entities.TipoImpressora;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class ConfiguracaoImpressoraMapper {
    
    public static ConfiguracaoImpressoraJpaEntity paraEntity(ConfiguracaoImpressoraEntity domain) {
        if (domain == null) {
            return null;
        }
        
        ConfiguracaoImpressoraJpaEntity entity = ConfiguracaoImpressoraJpaEntity.builder()
                .id(domain.getId())
                .tipoImpressora(domain.getTipoImpressora())
                .nomeEstabelecimento(domain.getNomeEstabelecimento())
                .enderecoEstabelecimento(domain.getEnderecoEstabelecimento())
                .telefoneEstabelecimento(domain.getTelefoneEstabelecimento())
                .cnpjEstabelecimento(domain.getCnpjEstabelecimento())
                .ativa(domain.isAtiva())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
        
        return entity;
    }
    
    public static ConfiguracaoImpressoraEntity paraDomain(ConfiguracaoImpressoraJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        
        ConfiguracaoImpressoraEntity domain = ConfiguracaoImpressoraEntity.criar(
                entity.getTipoImpressora(),
                entity.getNomeEstabelecimento(),
                entity.getEnderecoEstabelecimento(),
                entity.getTelefoneEstabelecimento(),
                entity.getCnpjEstabelecimento()
        );
        
        domain.restaurarDoBanco(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
        
        domain.restaurarEstadoAtivo(entity.isAtiva());
        
        return domain;
    }
    
    public static List<ConfiguracaoImpressoraEntity> paraDomainList(List<ConfiguracaoImpressoraJpaEntity> entities) {
        return entities.stream()
                .map(ConfiguracaoImpressoraMapper::paraDomain)
                .collect(Collectors.toList());
    }
}

