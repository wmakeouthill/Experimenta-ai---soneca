--liquibase formatted sql
--changeset snackbar:048-add-unique-conta-aberta
--comment: Garante no maximo uma conta ABERTA por (mesa, cliente) via coluna gerada + indice unico
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contas_mesa' AND index_name = 'uq_contas_mesa_aberta'

ALTER TABLE contas_mesa
    ADD COLUMN aberta_dedup VARCHAR(73)
    GENERATED ALWAYS AS (CASE WHEN status = 'ABERTA' THEN CONCAT(mesa_id, '|', cliente_id) END) VIRTUAL;

CREATE UNIQUE INDEX uq_contas_mesa_aberta ON contas_mesa (aberta_dedup);
