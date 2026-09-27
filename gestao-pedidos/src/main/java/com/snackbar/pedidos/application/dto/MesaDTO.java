package com.snackbar.pedidos.application.dto;

import com.snackbar.pedidos.domain.entities.Mesa;
import com.snackbar.pedidos.domain.entities.Piso;

import java.time.LocalDateTime;

/**
 * DTO de resposta para Mesa.
 */
public record MesaDTO(
        String id,
        int numero,
        String nome,
        String qrCodeToken,
        boolean ativa,
        Piso piso,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Converte uma entidade Mesa para DTO.
     */
    public static MesaDTO de(Mesa mesa) {
        return new MesaDTO(
                mesa.getId(),
                mesa.getNumero(),
                mesa.getNome(),
                mesa.getQrCodeTokenValor(),
                mesa.isAtiva(),
                mesa.getPiso(),
                mesa.getCreatedAt(),
                mesa.getUpdatedAt());
    }
}
