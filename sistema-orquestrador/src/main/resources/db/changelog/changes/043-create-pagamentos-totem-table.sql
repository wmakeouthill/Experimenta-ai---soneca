--liquibase formatted sql

--changeset snackbar:043-create-pagamentos-totem-table
--comment: Cria tabela de pagamentos do totem com idempotencia por correlation_id

CREATE TABLE IF NOT EXISTS pagamentos_totem (
    id VARCHAR(36) PRIMARY KEY,
    pedido_id VARCHAR(36) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    valor_centavos BIGINT NOT NULL,
    meio_pagamento VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    nsu_tef VARCHAR(50),
    bandeira VARCHAR(50),
    codigo_autorizacao VARCHAR(50),
    codigo_adquirente VARCHAR(50),
    comprovante_cliente TEXT,
    pix_txid VARCHAR(100),
    pix_qr_code_payload TEXT,
    pix_qr_code_base64 LONGTEXT,
    pix_copia_e_cola TEXT,
    pix_end_to_end_id VARCHAR(100),
    pix_expiracao_em TIMESTAMP NULL,
    motivo VARCHAR(500),
    iniciado_em TIMESTAMP NOT NULL,
    finalizado_em TIMESTAMP NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    UNIQUE INDEX idx_pagamentos_totem_correlation_id (correlation_id),
    UNIQUE INDEX idx_pagamentos_totem_pix_txid (pix_txid),
    INDEX idx_pagamentos_totem_pedido_id (pedido_id),
    INDEX idx_pagamentos_totem_status_inicio (status, iniciado_em),
    INDEX idx_pagamentos_totem_pix_end_to_end_id (pix_end_to_end_id),
    CONSTRAINT fk_pagamentos_totem_pedido
        FOREIGN KEY (pedido_id) REFERENCES pedidos(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE IF EXISTS pagamentos_totem;
