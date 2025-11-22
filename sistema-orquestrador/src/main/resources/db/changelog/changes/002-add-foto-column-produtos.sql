--liquibase formatted sql

--changeset snackbar:002-add-foto-column-produtos
--comment: Adiciona coluna foto na tabela produtos para armazenar imagem em base64

-- Adiciona coluna foto para armazenar imagem em base64
-- O Liquibase garante que este changeset será executado apenas uma vez
ALTER TABLE produtos 
ADD COLUMN foto TEXT NULL COMMENT 'Base64 string da imagem do produto' 
AFTER disponivel;

