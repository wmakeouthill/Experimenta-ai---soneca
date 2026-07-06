package com.snackbar.pedidos.infrastructure.gateway;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.snackbar.pedidos.application.ports.CartaoDigitalGatewayPort;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;

import lombok.extern.slf4j.Slf4j;

/**
 * Gateway de cartao digital simulado (default). Aprova qualquer cartao, exceto
 * os terminados em "0000" (recusa deterministica para testes). Nao chama rede.
 */
@Component
@ConditionalOnProperty(name = "pagamento.gateway.cartao-digital", havingValue = "SIMULADO", matchIfMissing = true)
@Slf4j
public class SimuladoCartaoDigitalAdapter implements CartaoDigitalGatewayPort {

    @Override
    public GatewayPagamento gateway() {
        return GatewayPagamento.SIMULADO;
    }

    @Override
    public ResultadoPagamentoCartao pagar(PagarCartaoCommand command) {
        String numero = command.cartao() != null ? command.cartao().numero() : null;
        boolean recusar = numero != null && numero.endsWith("0000");
        // NUNCA logar o comando/cartao - apenas metadados nao sensiveis.
        log.info("Cartao digital SIMULADO ref={} valor={} recusar={}",
                command.referencia(), command.valorCentavos(), recusar);
        if (recusar) {
            return new ResultadoPagamentoCartao(false, null, null, null, "Cartao recusado (simulado)");
        }
        return new ResultadoPagamentoCartao(true,
                "SIM-" + UUID.randomUUID(), "SIMULADO", "AUT" + System.currentTimeMillis() % 100000, null);
    }
}
