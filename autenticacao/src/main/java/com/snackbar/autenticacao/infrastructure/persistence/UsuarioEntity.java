package com.snackbar.autenticacao.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
public class UsuarioEntity implements Persistable<String> {
    
    @Id
    private String id;
    
    @Column(nullable = false, length = 100)
    private String nome;
    
    @Column(nullable = false, unique = true, length = 255)
    private String email;
    
    @Column(nullable = false, length = 255)
    private String senhaHash;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleEntity role;
    
    @Column(nullable = false)
    private Boolean ativo;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Transient
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
    
    public enum RoleEntity {
        ADMINISTRADOR,
        OPERADOR
    }
}

