package com.snackbar.cardapio.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "adicionais")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdicionalEntity implements Persistable<String> {
    @Id
    private String id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(length = 100)
    private String categoria;

    @Column(nullable = false)
    private boolean disponivel;

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

    public void markAsPersisted() {
        this.novo = false;
    }
}
