--liquibase formatted sql
--changeset snackbar:045-create-configuracao-pagamento
--comment: Tabela de configuracao (linha unica) das flags de pagamento por canal
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'configuracao_pagamento'

CREATE TABLE configuracao_pagamento (
    id VARCHAR(20) PRIMARY KEY,
    pix_totem_ativo BOOLEAN NOT NULL DEFAULT FALSE,
    cartao_totem_ativo BOOLEAN NOT NULL DEFAULT FALSE,
    pix_mesa_ativo BOOLEAN NOT NULL DEFAULT FALSE,
    cartao_mesa_ativo BOOLEAN NOT NULL DEFAULT FALSE,
    modo_mesa VARCHAR(10) NOT NULL DEFAULT 'PRE_PAGO',
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO configuracao_pagamento
    (id, pix_totem_ativo, cartao_totem_ativo, pix_mesa_ativo, cartao_mesa_ativo, modo_mesa, updated_at)
VALUES
    ('DEFAULT', FALSE, FALSE, FALSE, FALSE, 'PRE_PAGO', NOW());
