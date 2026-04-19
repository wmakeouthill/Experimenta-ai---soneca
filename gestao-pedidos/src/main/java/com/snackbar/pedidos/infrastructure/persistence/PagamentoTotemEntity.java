package com.snackbar.pedidos.infrastructure.persistence;

import java.time.LocalDateTime;

import org.springframework.data.domain.Persistable;

import com.snackbar.pedidos.domain.entities.MeioPagamentoTotem;
import com.snackbar.pedidos.domain.entities.StatusPagamentoTotem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pagamentos_totem")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoTotemEntity implements Persistable<String> {

    @Id
    private String id;

    @Column(name = "pedido_id", nullable = false, length = 36)
    private String pedidoId;

    @Column(name = "correlation_id", nullable = false, unique = true, length = 100)
    private String correlationId;

    @Column(name = "valor_centavos", nullable = false)
    private Long valorCentavos;

    @Enumerated(EnumType.STRING)
    @Column(name = "meio_pagamento", nullable = false, length = 30)
    private MeioPagamentoTotem meioPagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPagamentoTotem status;

    @Column(name = "nsu_tef", length = 50)
    private String nsuTef;

    @Column(length = 50)
    private String bandeira;

    @Column(name = "codigo_autorizacao", length = 50)
    private String codigoAutorizacao;

    @Column(name = "codigo_adquirente", length = 50)
    private String codigoAdquirente;

    @Column(name = "comprovante_cliente", columnDefinition = "TEXT")
    private String comprovanteCliente;

    @Column(name = "pix_txid", unique = true, length = 100)
    private String pixTxid;

    @Column(name = "pix_qr_code_payload", columnDefinition = "TEXT")
    private String pixQrCodePayload;

    @Column(name = "pix_qr_code_base64", columnDefinition = "LONGTEXT")
    private String pixQrCodeBase64;

    @Column(name = "pix_copia_e_cola", columnDefinition = "TEXT")
    private String pixCopiaECola;

    @Column(name = "pix_end_to_end_id", length = 100)
    private String pixEndToEndId;

    @Column(name = "pix_expiracao_em")
    private LocalDateTime pixExpiracaoEm;

    @Column(length = 500)
    private String motivo;

    @Column(name = "iniciado_em", nullable = false)
    private LocalDateTime iniciadoEm;

    @Column(name = "finalizado_em")
    private LocalDateTime finalizadoEm;

    @Version
    @Builder.Default
    private Long version = 0L;

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

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.novo = false;
    }
}
