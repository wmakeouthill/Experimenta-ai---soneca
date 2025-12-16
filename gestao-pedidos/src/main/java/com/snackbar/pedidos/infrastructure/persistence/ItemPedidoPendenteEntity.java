package com.snackbar.pedidos.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Entidade JPA para itens de pedido pendente.
 */
@Entity
@Table(name = "itens_pedido_pendente_mesa")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemPedidoPendenteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_pendente_id", nullable = false)
    private PedidoPendenteEntity pedidoPendente;

    @Column(name = "produto_id", nullable = false, length = 36)
    private String produtoId;

    @Column(name = "nome_produto", nullable = false, length = 200)
    private String nomeProduto;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(columnDefinition = "TEXT")
    private String observacoes;
}
