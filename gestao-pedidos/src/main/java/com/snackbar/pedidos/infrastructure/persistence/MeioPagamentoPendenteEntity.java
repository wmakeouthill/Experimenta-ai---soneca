package com.snackbar.pedidos.infrastructure.persistence;

import java.math.BigDecimal;

import com.snackbar.pedidos.domain.entities.MeioPagamento;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entidade JPA para meios de pagamento de pedido pendente de mesa.
 */
@Entity
@Table(name = "meios_pagamento_pendente_mesa")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeioPagamentoPendenteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_pendente_id", nullable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private PedidoPendenteEntity pedidoPendente;

    @Enumerated(EnumType.STRING)
    @Column(name = "meio_pagamento", nullable = false, length = 20)
    private MeioPagamento meioPagamento;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "valor_pago_dinheiro", precision = 10, scale = 2)
    private BigDecimal valorPagoDinheiro;

    @Column(name = "troco", precision = 10, scale = 2)
    private BigDecimal troco;
}
