package com.snackbar.pedidos.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entidade JPA para pedidos pendentes (mesa ou totem).
 * 
 * Pedidos criados por clientes via QR code (MESA) ou pelo totem (TOTEM)
 * ficam nesta tabela aguardando aceitação por um funcionário. Ao serem
 * aceitos, são convertidos para pedidos reais.
 * 
 * tipo = MESA: mesaToken, mesaId, numeroMesa preenchidos; clienteId opcional.
 * tipo = TOTEM: mesaToken, mesaId, numeroMesa, clienteId nulos; nomeCliente para chamar.
 */
@Entity
@Table(name = "pedidos_pendentes_mesa")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoPendenteEntity {

    public static final String TIPO_MESA = "MESA";
    public static final String TIPO_TOTEM = "TOTEM";

    @Id
    @Column(length = 36)
    private String id;

    /** Origem do pedido: MESA (QR code) ou TOTEM (auto atendimento). */
    @Column(name = "tipo", nullable = false, length = 10)
    @Builder.Default
    private String tipo = TIPO_MESA;

    @Column(name = "mesa_token", length = 100)
    private String mesaToken;

    @Column(name = "mesa_id", length = 36)
    private String mesaId;

    @Column(name = "numero_mesa")
    private Integer numeroMesa;

    @Column(name = "cliente_id", length = 36)
    private String clienteId;

    @Column(name = "nome_cliente", nullable = false, length = 200)
    private String nomeCliente;

    @Column(name = "telefone_cliente", length = 20)
    private String telefoneCliente;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "data_hora_solicitacao", nullable = false)
    private LocalDateTime dataHoraSolicitacao;

    @Column(name = "pedido_real_id", length = 36)
    private String pedidoRealId;

    @OneToMany(mappedBy = "pedidoPendente", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private Set<ItemPedidoPendenteEntity> itens = new HashSet<>();

    @OneToMany(mappedBy = "pedidoPendente", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private Set<MeioPagamentoPendenteEntity> meiosPagamento = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Adiciona um item ao pedido pendente.
     */
    public void adicionarItem(ItemPedidoPendenteEntity item) {
        itens.add(item);
        item.setPedidoPendente(this);
    }

    /**
     * Adiciona um meio de pagamento ao pedido pendente.
     */
    public void adicionarMeioPagamento(MeioPagamentoPendenteEntity meioPagamento) {
        meiosPagamento.add(meioPagamento);
        meioPagamento.setPedidoPendente(this);
    }
}
