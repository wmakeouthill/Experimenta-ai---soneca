package com.snackbar.pedidos.infrastructure.persistence;

import java.time.LocalDateTime;

import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "configuracao_pagamento")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracaoPagamentoEntity {

    public static final String ID_DEFAULT = "DEFAULT";

    @Id
    @Column(length = 20)
    private String id;

    @Column(name = "pix_totem_ativo", nullable = false)
    private boolean pixTotemAtivo;

    @Column(name = "cartao_totem_ativo", nullable = false)
    private boolean cartaoTotemAtivo;

    @Column(name = "pix_mesa_ativo", nullable = false)
    private boolean pixMesaAtivo;

    @Column(name = "cartao_mesa_ativo", nullable = false)
    private boolean cartaoMesaAtivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_mesa", nullable = false, length = 10)
    private ModoPagamentoMesa modoMesa;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
