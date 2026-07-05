package com.snackbar.pedidos.infrastructure.persistence;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;
import com.snackbar.pedidos.application.ports.ConfiguracaoPagamentoRepositoryPort;
import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConfiguracaoPagamentoRepositoryAdapter implements ConfiguracaoPagamentoRepositoryPort {

    private final ConfiguracaoPagamentoJpaRepository jpaRepository;

    @Override
    public ConfiguracaoPagamentoDTO buscar() {
        return jpaRepository.findById(ConfiguracaoPagamentoEntity.ID_DEFAULT)
                .map(this::paraDto)
                .orElseGet(() -> new ConfiguracaoPagamentoDTO(
                        false, false, false, false, ModoPagamentoMesa.PRE_PAGO));
    }

    @Override
    public ConfiguracaoPagamentoDTO salvar(ConfiguracaoPagamentoDTO config) {
        var entity = jpaRepository.findById(ConfiguracaoPagamentoEntity.ID_DEFAULT)
                .orElseGet(() -> ConfiguracaoPagamentoEntity.builder()
                        .id(ConfiguracaoPagamentoEntity.ID_DEFAULT)
                        .build());
        entity.setPixTotemAtivo(config.pixTotemAtivo());
        entity.setCartaoTotemAtivo(config.cartaoTotemAtivo());
        entity.setPixMesaAtivo(config.pixMesaAtivo());
        entity.setCartaoMesaAtivo(config.cartaoMesaAtivo());
        entity.setModoMesa(config.modoMesa());
        entity.setUpdatedAt(LocalDateTime.now());
        return paraDto(jpaRepository.save(entity));
    }

    private ConfiguracaoPagamentoDTO paraDto(ConfiguracaoPagamentoEntity entity) {
        return new ConfiguracaoPagamentoDTO(
                entity.isPixTotemAtivo(),
                entity.isCartaoTotemAtivo(),
                entity.isPixMesaAtivo(),
                entity.isCartaoMesaAtivo(),
                entity.getModoMesa());
    }
}
