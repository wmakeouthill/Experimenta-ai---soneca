package com.snackbar.clientes.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * Entidade JPA para persistência de Avaliações do Cliente.
 * Cliente pode avaliar produtos após realizar pedidos.
 */
@Entity
@Table(name = "cliente_avaliacoes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteAvaliacaoEntity implements Persistable<String> {

    @Id
    private String id;

    @Column(name = "cliente_id", nullable = false, length = 36)
    private String clienteId;

    @Column(name = "produto_id", nullable = false, length = 36)
    private String produtoId;

    @Column(name = "pedido_id", nullable = false, length = 36)
    private String pedidoId;

    @Column(nullable = false)
    private Integer nota; // 1 a 5

    @Column(columnDefinition = "TEXT")
    private String comentario;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Transient
    @Builder.Default
    private boolean novo = true;

    @Override
    public boolean isNew() {
        return novo;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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
