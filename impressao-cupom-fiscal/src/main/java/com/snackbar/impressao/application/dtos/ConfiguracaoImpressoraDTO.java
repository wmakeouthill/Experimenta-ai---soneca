package com.snackbar.impressao.application.dtos;

import com.snackbar.impressao.domain.entities.ConfiguracaoImpressoraEntity;
import com.snackbar.impressao.domain.entities.TipoImpressora;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracaoImpressoraDTO {
    private String id;
    private TipoImpressora tipoImpressora;
    private String nomeEstabelecimento;
    private String enderecoEstabelecimento;
    private String telefoneEstabelecimento;
    private String cnpjEstabelecimento;
    private boolean ativa;
    
    public static ConfiguracaoImpressoraDTO de(ConfiguracaoImpressoraEntity config) {
        return ConfiguracaoImpressoraDTO.builder()
                .id(config.getId())
                .tipoImpressora(config.getTipoImpressora())
                .nomeEstabelecimento(config.getNomeEstabelecimento())
                .enderecoEstabelecimento(config.getEnderecoEstabelecimento())
                .telefoneEstabelecimento(config.getTelefoneEstabelecimento())
                .cnpjEstabelecimento(config.getCnpjEstabelecimento())
                .ativa(config.isAtiva())
                .build();
    }
}

