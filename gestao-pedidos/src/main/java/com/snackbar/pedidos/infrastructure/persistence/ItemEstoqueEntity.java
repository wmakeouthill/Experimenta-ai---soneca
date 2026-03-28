package com.snackbar.pedidos.infrastructure.persistence;

import com.snackbar.pedidos.domain.entities.UnidadeMedida;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "itens_estoque")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemEstoqueEntity implements Persistable<String> {
    @Id
    private String id;
    
    @Column(nullable = false, length = 150, unique = true)
    private String nome;
    
    @Column(length = 500)
    private String descricao;
    
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;
    
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidadeMinima;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UnidadeMedida unidadeMedida;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal precoUnitario;
    
    @Column(length = 200)
    private String fornecedor;
    
    @Column(length = 50)
    private String codigoBarras;
    
    @Column(nullable = false)
    private boolean ativo;
    
    @Version
    @Builder.Default
    private Long version = 0L;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Transient
    @Builder.Default
    private boolean novo = true;

    @Override
    public boolean isNew() {
        return novo;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.novo = false;
    }
}

