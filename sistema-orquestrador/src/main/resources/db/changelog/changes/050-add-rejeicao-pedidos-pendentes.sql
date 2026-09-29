--liquibase formatted sql

--changeset snackbar:050-add-rejeicao-pedidos-pendentes
--comment: Pedido pendente rejeitado fica marcado (em vez de apagado) para o cliente ver o motivo no status
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pedidos_pendentes_mesa' AND column_name = 'rejeitado_em'
ALTER TABLE pedidos_pendentes_mesa
    ADD COLUMN rejeitado_em DATETIME NULL,
    ADD COLUMN motivo_rejeicao VARCHAR(500) NULL;
