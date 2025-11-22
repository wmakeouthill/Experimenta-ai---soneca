--liquibase formatted sql

--changeset snackbar:005-update-pendente-to-preparando
--comment: Migration: Atualiza pedidos com status PENDENTE para PREPARANDO (status PENDENTE não é mais usado)

-- Atualiza todos os pedidos que estão com status PENDENTE para PREPARANDO
UPDATE pedidos 
SET status = 'PREPARANDO', updated_at = NOW()
WHERE status = 'PENDENTE';

