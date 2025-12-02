--liquibase formatted sql

--changeset snackbar:021-remover-pedido-id-movimentacoes-caixa
--comment: Remove coluna pedido_id da tabela movimentacoes_caixa (vendas são buscadas da tabela pedidos)

-- Primeiro remove a foreign key constraint
ALTER TABLE movimentacoes_caixa
    DROP FOREIGN KEY fk_movimentacoes_caixa_pedido;

-- Remove o índice da coluna
DROP INDEX idx_movimentacoes_caixa_pedido_id ON movimentacoes_caixa;

-- Depois remove a coluna
ALTER TABLE movimentacoes_caixa
    DROP COLUMN pedido_id;

--rollback ALTER TABLE movimentacoes_caixa ADD COLUMN pedido_id VARCHAR(36) NULL;
--rollback CREATE INDEX idx_movimentacoes_caixa_pedido_id ON movimentacoes_caixa(pedido_id);
--rollback ALTER TABLE movimentacoes_caixa ADD CONSTRAINT fk_movimentacoes_caixa_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id) ON DELETE SET NULL;

