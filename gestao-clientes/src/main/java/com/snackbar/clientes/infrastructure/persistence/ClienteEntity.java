package com.snackbar.clientes.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table(name = "clientes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteEntity implements Persistable<String> {
    @Id
    private String id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(length = 20)
    private String telefone;

    @Column(length = 200)
    private String email;

    @Column(length = 14)
    private String cpf;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    // ========== Campos de Autenticação ==========

    @Column(name = "senha_hash", length = 255)
    private String senhaHash;

    @Column(name = "google_id", length = 255, unique = true)
    private String googleId;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @Column(name = "email_verificado", nullable = false)
    @Builder.Default
    private Boolean emailVerificado = false;

    @Column(name = "ultimo_login")
    private LocalDateTime ultimoLogin;

    // ========== Timestamps ==========

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
