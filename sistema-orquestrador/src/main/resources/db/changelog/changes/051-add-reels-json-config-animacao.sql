--liquibase formatted sql

--changeset snackbar:051-add-reels-json-config-animacao
--comment: Guarda os reels promocionais do lobby sem alterar videos nem intervalo
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'config_animacao' AND column_name = 'reels_json'

ALTER TABLE config_animacao
    ADD COLUMN reels_json LONGTEXT NULL;

--rollback ALTER TABLE config_animacao DROP COLUMN reels_json;
