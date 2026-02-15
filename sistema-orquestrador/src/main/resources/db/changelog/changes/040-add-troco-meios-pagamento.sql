--liquibase formatted sql

--changeset snackbar:040-add-troco-meios-pagamento
--comment: Adiciona campos de valor pago em dinheiro e troco nas tabelas de meios de pagamento

-- Tabela principal: meios_pagamento_pedido
ALTER TABLE meios_pagamento_pedido
    ADD COLUMN valor_pago_dinheiro DECIMAL(10, 2) NULL COMMENT 'Valor pago em nota pelo cliente (apenas para DINHEIRO)',
    ADD COLUMN troco DECIMAL(10, 2) NULL COMMENT 'Valor do troco calculado (valorPagoDinheiro - valor)';

-- Tabela de pedidos pendentes de mesa: meios_pagamento_pendente_mesa
ALTER TABLE meios_pagamento_pendente_mesa
    ADD COLUMN valor_pago_dinheiro DECIMAL(10, 2) NULL COMMENT 'Valor pago em nota pelo cliente (apenas para DINHEIRO)',
    ADD COLUMN troco DECIMAL(10, 2) NULL COMMENT 'Valor do troco calculado (valorPagoDinheiro - valor)';

--rollback ALTER TABLE meios_pagamento_pedido DROP COLUMN valor_pago_dinheiro, DROP COLUMN troco;
--rollback ALTER TABLE meios_pagamento_pendente_mesa DROP COLUMN valor_pago_dinheiro, DROP COLUMN troco;
