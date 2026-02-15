package com.snackbar.pedidos.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resposta pública do status da loja (sessão de trabalho).
 * Usado para bloquear pedidos quando não há sessão ativa.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusLojaResponse {

    /** Status da loja: ABERTA, PAUSADA ou FECHADA */
    private String status;

    /** Mensagem descritiva para o cliente */
    private String mensagem;

    /** Número da sessão ativa (null se fechada) */
    private Integer numeroSessao;
}
