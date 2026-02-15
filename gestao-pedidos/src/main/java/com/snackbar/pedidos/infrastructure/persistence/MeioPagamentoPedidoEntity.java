package com.snackbar.pedidos.infrastructure.persistence;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "meios_pagamento_pedido")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeioPagamentoPedidoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private PedidoEntity pedido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private com.snackbar.pedidos.domain.entities.MeioPagamento meioPagamento;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "valor_pago_dinheiro", precision = 10, scale = 2)
    private BigDecimal valorPagoDinheiro;

    @Column(name = "troco", precision = 10, scale = 2)
    private BigDecimal troco;
}
