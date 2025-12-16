-- Liquibase changeset para adicionar colunas de versão para Optimistic Locking
-- Previne lost updates em cenários de concorrência

-- changeset dev:027-add-version-columns-optimistic-locking

-- Adiciona coluna version na tabela pedidos para controle de concorrência
ALTER TABLE pedidos ADD COLUMN version BIGINT DEFAULT 0;

-- Adiciona coluna version na tabela sessoes_trabalho para controle de concorrência
ALTER TABLE sessoes_trabalho ADD COLUMN version BIGINT DEFAULT 0;

-- Atualiza registros existentes para ter version = 0
UPDATE pedidos SET version = 0 WHERE version IS NULL;
UPDATE sessoes_trabalho SET version = 0 WHERE version IS NULL;

-- Remove o DEFAULT após atualizar os registros existentes (opcional, para manter consistência)
-- A JPA/Hibernate gerenciará automaticamente o incremento da versão
