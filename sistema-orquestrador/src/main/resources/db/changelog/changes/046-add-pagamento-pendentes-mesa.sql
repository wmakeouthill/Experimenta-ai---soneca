--liquibase formatted sql

--changeset snackbar:046-add-aguardando-pagamento-pendentes-mesa
--comment: Adiciona coluna aguardando_pagamento aos pedidos pendentes de mesa
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pedidos_pendentes_mesa' AND column_name = 'aguardando_pagamento'
ALTER TABLE pedidos_pendentes_mesa
    ADD COLUMN aguardando_pagamento BOOLEAN NOT NULL DEFAULT FALSE;

--changeset snackbar:046-add-pagamento-correlation-id-pendentes-mesa
--comment: Adiciona correlation id do pagamento PIX aos pedidos pendentes de mesa
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pedidos_pendentes_mesa' AND column_name = 'pagamento_correlation_id'
ALTER TABLE pedidos_pendentes_mesa
    ADD COLUMN pagamento_correlation_id VARCHAR(100) NULL;

--changeset snackbar:046-add-idx-aguardando-pagamento-pendentes-mesa
--comment: Adiciona indice para filtrar pedidos pendentes aguardando pagamento
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'pedidos_pendentes_mesa' AND index_name = 'idx_pedidos_pendentes_aguardando_pagamento'
CREATE INDEX idx_pedidos_pendentes_aguardando_pagamento
    ON pedidos_pendentes_mesa (aguardando_pagamento);