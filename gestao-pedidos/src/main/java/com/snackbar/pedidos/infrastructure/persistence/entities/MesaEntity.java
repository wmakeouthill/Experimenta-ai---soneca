package com.snackbar.pedidos.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * Entidade JPA para persistência de Mesa.
 */
@Entity
@Table(name = "mesas")
@Data
@NoArgsConstructor
public class MesaEntity implements Persistable<String> {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true)
    private Integer numero;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "qr_code_token", nullable = false, unique = true, length = 64)
    private String qrCodeToken;

    @Column(nullable = false)
    private Boolean ativa = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Transient
    private boolean novo = true;

    @Override
    public boolean isNew() {
        return novo;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
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
