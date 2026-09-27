--liquibase formatted sql

--changeset snackbar:049-add-piso-pedidos
--comment: Piso onde o pedido aparece no painel (NULL = pedido legado, aparece nos dois pisos)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name = 'piso'
ALTER TABLE pedidos
    ADD COLUMN piso VARCHAR(10) NULL;

--changeset snackbar:049-add-piso-pedidos-pendentes-mesa
--comment: Piso carregado pelo pedido enquanto aguarda aceite na fila (mesa ou totem)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pedidos_pendentes_mesa' AND column_name = 'piso'
ALTER TABLE pedidos_pendentes_mesa
    ADD COLUMN piso VARCHAR(10) NULL;

--changeset snackbar:049-add-piso-mesas
--comment: Piso fisico da mesa; pedidos da mesa herdam este piso
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'mesas' AND column_name = 'piso'
ALTER TABLE mesas
    ADD COLUMN piso VARCHAR(10) NOT NULL DEFAULT 'TERREO';
