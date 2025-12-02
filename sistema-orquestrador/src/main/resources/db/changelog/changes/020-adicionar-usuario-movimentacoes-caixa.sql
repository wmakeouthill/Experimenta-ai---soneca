--liquibase formatted sql

--changeset snackbar:020-adicionar-usuario-movimentacoes-caixa
--comment: Adiciona coluna usuario_id na tabela movimentacoes_caixa

ALTER TABLE movimentacoes_caixa
    ADD COLUMN usuario_id VARCHAR(36) NULL;

CREATE INDEX idx_movimentacoes_caixa_usuario_id ON movimentacoes_caixa(usuario_id);

--rollback ALTER TABLE movimentacoes_caixa DROP COLUMN usuario_id;
--rollback DROP INDEX idx_movimentacoes_caixa_usuario_id ON movimentacoes_caixa;

