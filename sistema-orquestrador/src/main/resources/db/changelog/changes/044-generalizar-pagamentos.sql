--liquibase formatted sql
--changeset snackbar:044-rename-pagamentos-totem
--comment: Renomeia pagamentos_totem para pagamentos (suporte a canais TOTEM/MESA e multiplos gateways)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'pagamentos_totem'

RENAME TABLE pagamentos_totem TO pagamentos;

--changeset snackbar:044-add-canal-gateway-referencias
--comment: Adiciona canal, gateway e referencias alternativas (pedido pendente, conta de mesa) e id de pagamento no gateway
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pagamentos' AND column_name = 'canal'

ALTER TABLE pagamentos
    ADD COLUMN canal VARCHAR(10) NOT NULL DEFAULT 'TOTEM' AFTER id,
    ADD COLUMN gateway VARCHAR(10) NOT NULL DEFAULT 'SIMULADO' AFTER canal,
    ADD COLUMN pedido_pendente_id VARCHAR(36) NULL AFTER pedido_id,
    ADD COLUMN conta_mesa_id VARCHAR(36) NULL AFTER pedido_pendente_id,
    ADD COLUMN gateway_payment_id VARCHAR(64) NULL AFTER correlation_id,
    MODIFY COLUMN pedido_id VARCHAR(36) NULL;

CREATE INDEX idx_pagamentos_canal ON pagamentos (canal);
CREATE INDEX idx_pagamentos_pedido_pendente ON pagamentos (pedido_pendente_id);
CREATE INDEX idx_pagamentos_conta_mesa ON pagamentos (conta_mesa_id);
CREATE INDEX idx_pagamentos_gateway_payment ON pagamentos (gateway_payment_id);
