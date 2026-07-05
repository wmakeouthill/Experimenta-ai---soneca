package com.snackbar.pedidos.application.ports;

import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;

public interface ConfiguracaoPagamentoRepositoryPort {

    ConfiguracaoPagamentoDTO buscar();

    ConfiguracaoPagamentoDTO salvar(ConfiguracaoPagamentoDTO config);
}
