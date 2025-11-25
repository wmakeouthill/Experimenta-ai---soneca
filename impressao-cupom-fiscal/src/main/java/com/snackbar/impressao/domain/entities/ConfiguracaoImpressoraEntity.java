package com.snackbar.impressao.domain.entities;

import com.snackbar.kernel.domain.entities.BaseEntity;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ConfiguracaoImpressoraEntity extends BaseEntity {
    private TipoImpressora tipoImpressora;
    private String nomeEstabelecimento;
    private String enderecoEstabelecimento;
    private String telefoneEstabelecimento;
    private String cnpjEstabelecimento;
    private String logoBase64;
    private byte[] logoEscPos;
    private boolean ativa;
    
    private ConfiguracaoImpressoraEntity() {
        super();
        this.ativa = true;
    }
    
    public static ConfiguracaoImpressoraEntity criar(
            TipoImpressora tipoImpressora,
            String nomeEstabelecimento,
            String enderecoEstabelecimento,
            String telefoneEstabelecimento,
            String cnpjEstabelecimento,
            String logoBase64) {
        validarDados(tipoImpressora, nomeEstabelecimento);
        
        ConfiguracaoImpressoraEntity config = new ConfiguracaoImpressoraEntity();
        config.tipoImpressora = tipoImpressora;
        config.nomeEstabelecimento = nomeEstabelecimento;
        config.enderecoEstabelecimento = enderecoEstabelecimento;
        config.telefoneEstabelecimento = telefoneEstabelecimento;
        config.cnpjEstabelecimento = cnpjEstabelecimento;
        config.logoBase64 = logoBase64;
        config.logoEscPos = converterLogoParaEscPos(logoBase64);
        config.touch();
        return config;
    }
    
    public void atualizar(
            TipoImpressora tipoImpressora,
            String nomeEstabelecimento,
            String enderecoEstabelecimento,
            String telefoneEstabelecimento,
            String cnpjEstabelecimento,
            String logoBase64) {
        validarDados(tipoImpressora, nomeEstabelecimento);
        
        this.tipoImpressora = tipoImpressora;
        this.nomeEstabelecimento = nomeEstabelecimento;
        this.enderecoEstabelecimento = enderecoEstabelecimento;
        this.telefoneEstabelecimento = telefoneEstabelecimento;
        this.cnpjEstabelecimento = cnpjEstabelecimento;
        this.logoBase64 = logoBase64;
        this.logoEscPos = converterLogoParaEscPos(logoBase64);
        touch();
    }
    
    private static byte[] converterLogoParaEscPos(String logoBase64) {
        if (logoBase64 == null || logoBase64.trim().isEmpty()) {
            return null;
        }
        return com.snackbar.impressao.infrastructure.impressora.ImagemEscPosUtil.converterBase64ParaEscPos(logoBase64);
    }
    
    public void ativar() {
        this.ativa = true;
        touch();
    }
    
    public void desativar() {
        this.ativa = false;
        touch();
    }
    
    public void restaurarEstadoAtivo(boolean ativa) {
        this.ativa = ativa;
    }
    
    public void restaurarLogoEscPos(byte[] logoEscPos) {
        this.logoEscPos = logoEscPos;
    }
    
    public void restaurarDoBanco(String id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        restaurarId(id);
        restaurarTimestamps(createdAt, updatedAt);
    }
    
    private static void validarDados(TipoImpressora tipoImpressora, String nomeEstabelecimento) {
        if (tipoImpressora == null) {
            throw new IllegalArgumentException("Tipo de impressora não pode ser nulo");
        }
        if (nomeEstabelecimento == null || nomeEstabelecimento.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do estabelecimento não pode ser nulo ou vazio");
        }
    }
}

