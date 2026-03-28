--liquibase formatted sql
--changeset snackbar:042-add-version-column-produtos
--comment: Adiciona coluna version na tabela produtos para Optimistic Locking
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'produtos' AND column_name = 'version'

ALTER TABLE produtos ADD COLUMN version BIGINT DEFAULT 0;

--changeset snackbar:042-add-version-column-clientes
--comment: Adiciona coluna version na tabela clientes para Optimistic Locking
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'clientes' AND column_name = 'version'

ALTER TABLE clientes ADD COLUMN version BIGINT DEFAULT 0;

--changeset snackbar:042-add-version-column-itens-estoque
--comment: Adiciona coluna version na tabela itens_estoque para Optimistic Locking
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itens_estoque' AND column_name = 'version'

ALTER TABLE itens_estoque ADD COLUMN version BIGINT DEFAULT 0;

--changeset snackbar:042-update-version-produtos
--comment: Atualiza registros existentes de produtos para ter version = 0

UPDATE produtos SET version = 0 WHERE version IS NULL;

--changeset snackbar:042-update-version-clientes
--comment: Atualiza registros existentes de clientes para ter version = 0

UPDATE clientes SET version = 0 WHERE version IS NULL;

--changeset snackbar:042-update-version-itens-estoque
--comment: Atualiza registros existentes de itens_estoque para ter version = 0

UPDATE itens_estoque SET version = 0 WHERE version IS NULL;
