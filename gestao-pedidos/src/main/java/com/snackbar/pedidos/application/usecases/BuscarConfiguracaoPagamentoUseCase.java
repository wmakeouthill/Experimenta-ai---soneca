package com.snackbar.pedidos.application.usecases;

import org.springframework.stereotype.Service;

import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;
import com.snackbar.pedidos.application.ports.ConfiguracaoPagamentoRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BuscarConfiguracaoPagamentoUseCase {

    private final ConfiguracaoPagamentoRepositoryPort configuracaoRepository;

    public ConfiguracaoPagamentoDTO executar() {
        return configuracaoRepository.buscar();
    }
}
