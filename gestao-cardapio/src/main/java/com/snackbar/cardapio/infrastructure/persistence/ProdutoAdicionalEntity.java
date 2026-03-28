package com.snackbar.cardapio.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table(name = "produtos_adicionais")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(ProdutoAdicionalId.class)
public class ProdutoAdicionalEntity implements Persistable<ProdutoAdicionalId> {

    @Id
    @Column(name = "produto_id", nullable = false, length = 36)
    private String produtoId;

    @Id
    @Column(name = "adicional_id", nullable = false, length = 36)
    private String adicionalId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Transient
    @Builder.Default
    private boolean novo = true;

    @Override
    public ProdutoAdicionalId getId() {
        return new ProdutoAdicionalId(produtoId, adicionalId);
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.novo = false;
    }
}
