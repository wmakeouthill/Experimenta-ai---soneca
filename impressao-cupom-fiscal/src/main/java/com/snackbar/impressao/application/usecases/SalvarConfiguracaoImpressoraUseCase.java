package com.snackbar.impressao.application.usecases;

import com.snackbar.impressao.application.dtos.ConfiguracaoImpressoraDTO;
import com.snackbar.impressao.application.dtos.SalvarConfiguracaoImpressoraRequest;
import com.snackbar.impressao.application.ports.ConfiguracaoImpressoraRepositoryPort;
import com.snackbar.impressao.domain.entities.ConfiguracaoImpressoraEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalvarConfiguracaoImpressoraUseCase {
    
    private final ConfiguracaoImpressoraRepositoryPort repository;
    
    public ConfiguracaoImpressoraDTO executar(SalvarConfiguracaoImpressoraRequest request) {
        ConfiguracaoImpressoraEntity configAtiva = buscarConfigAtiva();
        
        if (configAtiva != null) {
            configAtiva.atualizar(
                    request.getTipoImpressora(),
                    request.getNomeEstabelecimento(),
                    request.getEnderecoEstabelecimento(),
                    request.getTelefoneEstabelecimento(),
                    request.getCnpjEstabelecimento(),
                    request.getLogoBase64()
            );
            ConfiguracaoImpressoraEntity salva = repository.salvar(configAtiva);
            return ConfiguracaoImpressoraDTO.de(salva);
        } else {
            ConfiguracaoImpressoraEntity novaConfig = ConfiguracaoImpressoraEntity.criar(
                    request.getTipoImpressora(),
                    request.getNomeEstabelecimento(),
                    request.getEnderecoEstabelecimento(),
                    request.getTelefoneEstabelecimento(),
                    request.getCnpjEstabelecimento(),
                    request.getLogoBase64()
            );
            ConfiguracaoImpressoraEntity salva = repository.salvar(novaConfig);
            return ConfiguracaoImpressoraDTO.de(salva);
        }
    }
    
    private ConfiguracaoImpressoraEntity buscarConfigAtiva() {
        return repository.buscarAtiva().orElse(null);
    }
}

