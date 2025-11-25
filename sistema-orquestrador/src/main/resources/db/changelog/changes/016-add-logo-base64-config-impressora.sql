--liquibase formatted sql

--changeset snackbar:016-add-logo-base64-config-impressora
--comment: Migration: Adiciona colunas logo_base64 e logo_esc_pos na tabela config_impressora

ALTER TABLE config_impressora 
ADD COLUMN logo_base64 LONGTEXT NULL COMMENT 'Imagem do logo em base64 (original)'
AFTER cnpj_estabelecimento;

ALTER TABLE config_impressora 
ADD COLUMN logo_esc_pos LONGBLOB NULL COMMENT 'Logo já convertido para bitmap ESC/POS (otimizado para impressão)'
AFTER logo_base64;

