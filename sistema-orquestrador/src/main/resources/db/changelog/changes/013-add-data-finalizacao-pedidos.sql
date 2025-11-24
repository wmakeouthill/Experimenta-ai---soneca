--liquibase formatted sql

--changeset snackbar:013-add-data-finalizacao-pedidos
--comment: Migration: Adiciona coluna data_finalizacao na tabela pedidos para armazenar data definitiva de finalização

ALTER TABLE pedidos 
ADD COLUMN data_finalizacao TIMESTAMP NULL 
AFTER data_pedido;

-- Índice para consultas por data de finalização
CREATE INDEX idx_pedidos_data_finalizacao ON pedidos(data_finalizacao);

