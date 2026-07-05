package com.snackbar.pedidos.application.usecases;

import org.springframework.stereotype.Service;

import com.snackbar.pedidos.application.dto.ConfigPagamentoPublicaDTO;
import com.snackbar.pedidos.application.dto.ConfigPagamentoPublicaDTO.CanalConfig;
import com.snackbar.pedidos.application.dto.ConfigPagamentoPublicaDTO.MesaConfig;
import com.snackbar.pedidos.application.ports.ConfiguracaoPagamentoRepositoryPort;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.infrastructure.config.PagamentoProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BuscarConfigPagamentoPublicaUseCase {

    private final ConfiguracaoPagamentoRepositoryPort configuracaoRepository;
    private final PagamentoProperties properties;

    public ConfigPagamentoPublicaDTO executar() {
        if (!properties.isEnabled()) {
            return ConfigPagamentoPublicaDTO.desativada();
        }

        var flags = configuracaoRepository.buscar();
        boolean pixSimulado = properties.getGateway().getPix() == GatewayPagamento.SIMULADO;

        return new ConfigPagamentoPublicaDTO(
                true,
                new CanalConfig(flags.pixTotemAtivo(), flags.cartaoTotemAtivo()),
                new MesaConfig(flags.pixMesaAtivo(), flags.cartaoMesaAtivo(), flags.modoMesa()),
                pixSimulado);
    }
}
