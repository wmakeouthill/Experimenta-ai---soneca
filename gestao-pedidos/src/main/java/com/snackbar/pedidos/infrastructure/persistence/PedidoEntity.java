package com.snackbar.pedidos.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true, length = 10)
    private String numeroPedido;
    
    @Column(nullable = false, length = 36)
    private String clienteId;
    
    @Column(nullable = false, length = 200)
    private String clienteNome;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private com.snackbar.pedidos.domain.entities.StatusPedido status;
    
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ItemPedidoEntity> itens = new ArrayList<>();
    
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MeioPagamentoPedidoEntity> meiosPagamento = new ArrayList<>();
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;
    
    @Column(columnDefinition = "TEXT")
    private String observacoes;
    
    @Column(length = 36)
    private String usuarioId;
    
    @Column(nullable = false)
    private LocalDateTime dataPedido;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

