package com.snackbar.pedidos.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.snackbar.pedidos.domain.entities.StatusContaMesa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "contas_mesa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContaMesaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "mesa_id", length = 36, nullable = false)
    private String mesaId;

    @Column(name = "numero_mesa", nullable = false)
    private int numeroMesa;

    @Column(name = "cliente_id", length = 36, nullable = false)
    private String clienteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StatusContaMesa status;

    @Column(name = "valor_centavos", nullable = false)
    private long valorCentavos;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "conta_mesa_id")
    @Builder.Default
    private Set<ContaMesaPedidoEntity> pedidos = new HashSet<>();
}
