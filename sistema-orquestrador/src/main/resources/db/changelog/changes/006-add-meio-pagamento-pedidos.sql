--liquibase formatted sql

--changeset snackbar:006-add-meio-pagamento-pedidos
--comment: Migration: Adiciona coluna meio_pagamento na tabela pedidos

ALTER TABLE pedidos 
ADD COLUMN meio_pagamento VARCHAR(20) NULL 
AFTER observacoes;

--rollback ALTER TABLE pedidos DROP COLUMN meio_pagamento;

