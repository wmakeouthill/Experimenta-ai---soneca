package com.snackbar.pedidos.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO com o status do pedido para o cliente.
 * Usado para polling do cliente após fazer um pedido.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusPedidoClienteDTO {

    /**
     * Status do pedido do ponto de vista do cliente.
     */
    public enum StatusCliente {
        /** Pedido na fila, aguardando funcionário aceitar */
        AGUARDANDO_ACEITACAO("Aguardando confirmação"),

        /** Pedido aceito, aguardando início do preparo */
        ACEITO("Pedido confirmado"),

        /** Pedido sendo preparado */
        PREPARANDO("Em preparação"),

        /** Pedido pronto para ser servido/retirado */
        PRONTO("Pronto"),

        /** Pedido entregue/finalizado */
        FINALIZADO("Finalizado"),

        /** Pedido cancelado/rejeitado */
        CANCELADO("Cancelado");

        private final String descricao;

        StatusCliente(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }
    }

    private String pedidoId;
    private StatusCliente status;
    private String statusDescricao;
    private Integer numeroMesa;
    private LocalDateTime dataHoraSolicitacao;
    private long tempoEsperaSegundos;

    /** Número global do pedido (só existe após aceito). */
    private Integer numeroPedido;

    /** Posição de chamada na sessão, no formato 01, 02. */
    private String numeroExibicao;

    /** Motivo do cancelamento/rejeição (quando aplicável) */
    private String motivoCancelamento;
}
