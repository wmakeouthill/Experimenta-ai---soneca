--liquibase formatted sql
--changeset snackbar:046-add-aguardando-pagamento-pendentes-mesa
--comment: Adiciona controle de pagamento digital (PIX pre-pago) aos pedidos pendentes de mesa
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pedidos_pendentes_mesa' AND column_name = 'aguardando_pagamento'

ALTER TABLE pedidos_pendentes_mesa
    ADD COLUMN aguardando_pagamento BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN pagamento_correlation_id VARCHAR(36) NULL;

CREATE INDEX idx_pedidos_pendentes_aguardando_pagamento
    ON pedidos_pendentes_mesa (aguardando_pagamento);