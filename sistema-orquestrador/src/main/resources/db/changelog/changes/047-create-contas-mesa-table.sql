--liquibase formatted sql
--changeset snackbar:047-create-contas-mesa-table
--comment: Cria as tabelas de conta pos-paga de mesa (contas_mesa e conta_mesa_pedidos)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'contas_mesa'

CREATE TABLE contas_mesa (
    id VARCHAR(36) NOT NULL,
    mesa_id VARCHAR(36) NOT NULL,
    numero_mesa INT NOT NULL,
    cliente_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    valor_centavos BIGINT NOT NULL,
    correlation_id VARCHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_contas_mesa PRIMARY KEY (id)
);

CREATE INDEX idx_contas_mesa_mesa_cliente_status
    ON contas_mesa (mesa_id, cliente_id, status);

CREATE TABLE conta_mesa_pedidos (
    conta_mesa_id VARCHAR(36) NOT NULL,
    pedido_id VARCHAR(36) NOT NULL,
    CONSTRAINT pk_conta_mesa_pedidos PRIMARY KEY (conta_mesa_id, pedido_id),
    CONSTRAINT fk_conta_mesa_pedidos_conta FOREIGN KEY (conta_mesa_id)
        REFERENCES contas_mesa (id) ON DELETE CASCADE
);
