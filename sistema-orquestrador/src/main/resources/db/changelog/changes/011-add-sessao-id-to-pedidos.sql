--liquibase formatted sql

--changeset snackbar:011-add-sessao-id-to-pedidos
--comment: Migration: Adiciona coluna sessao_id na tabela pedidos para vincular pedidos a sessões de trabalho

-- Adiciona coluna sessao_id na tabela pedidos
ALTER TABLE pedidos 
ADD COLUMN sessao_id VARCHAR(36) NULL,
ADD INDEX idx_pedidos_sessao_id (sessao_id),
ADD FOREIGN KEY (sessao_id) REFERENCES sessoes_trabalho(id) ON DELETE RESTRICT;

--rollback ALTER TABLE pedidos DROP FOREIGN KEY pedidos_ibfk_2, DROP INDEX idx_pedidos_sessao_id, DROP COLUMN sessao_id;

