package com.snackbar.pedidos.application.dto;

import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;

/**
 * Configuracao efetiva exposta publicamente (totem e pedido-mesa):
 * combina o master switch (env), as flags do banco e o gateway ativo.
 * Nao expoe credenciais nem detalhes de infraestrutura.
 */
public record ConfigPagamentoPublicaDTO(
        boolean pagamentosAtivos,
        CanalConfig totem,
        MesaConfig mesa,
        boolean gatewayPixSimulado) {

    public record CanalConfig(boolean pixAtivo, boolean cartaoAtivo) {
    }

    public record MesaConfig(boolean pixAtivo, boolean cartaoAtivo, ModoPagamentoMesa modo) {
    }

    public static ConfigPagamentoPublicaDTO desativada() {
        return new ConfigPagamentoPublicaDTO(
                false,
                new CanalConfig(false, false),
                new MesaConfig(false, false, ModoPagamentoMesa.PRE_PAGO),
                false);
    }
}
