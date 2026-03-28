package com.snackbar.cardapio.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table(name = "categorias")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaEntity implements Persistable<String> {
    @Id
    private String id;
    
    @Column(nullable = false, length = 200)
    private String nome;
    
    @Column(columnDefinition = "TEXT")
    private String descricao;
    
    @Column(nullable = false)
    private boolean ativa;
    
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

