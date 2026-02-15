--liquibase formatted sql
--changeset snackbar:041-add-tipo-column-pedidos-pendentes
--comment: Adiciona coluna tipo (MESA/TOTEM) na tabela pedidos_pendentes_mesa para suportar pedidos de totem na mesma tabela
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pedidos_pendentes_mesa' AND column_name = 'tipo'

ALTER TABLE pedidos_pendentes_mesa
    ADD COLUMN tipo VARCHAR(10) NOT NULL DEFAULT 'MESA' AFTER id;

CREATE INDEX idx_pedidos_pendentes_tipo ON pedidos_pendentes_mesa (tipo);

--changeset snackbar:041-make-mesa-columns-nullable
--comment: Torna colunas de mesa nullable para permitir pedidos de totem (que não têm mesa)

ALTER TABLE pedidos_pendentes_mesa
    MODIFY COLUMN mesa_token VARCHAR(100) NULL,
    MODIFY COLUMN mesa_id VARCHAR(36) NULL,
    MODIFY COLUMN numero_mesa INT NULL;
