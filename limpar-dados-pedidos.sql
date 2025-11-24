-- ============================================================
-- Script: Limpar Dados de Pedidos e Sessões de Trabalho
-- Descrição: Remove todos os dados relacionados a pedidos e sessões de trabalho,
--            mantendo produtos, categorias, clientes e usuários
-- ============================================================
-- 
-- IMPORTANTE: 
-- - Este script deleta TODOS os pedidos, itens de pedidos, meios de pagamento E sessões de trabalho
-- - Os dados de produtos, categorias, clientes e usuários serão preservados
-- - Execute com cuidado em ambiente de produção
--
-- Como executar no IntelliJ:
-- 1. Abra o Data Source configurado no IntelliJ
-- 2. Clique com botão direito no banco de dados
-- 3. Selecione "New" > "Query Console" ou abra este arquivo
-- 4. Execute o script selecionando todo o conteúdo (Ctrl+A) e depois (Ctrl+Enter)
-- ============================================================

-- ============================================================
-- VERIFICAÇÃO PRÉ-LIMPEZA
-- ============================================================

-- Verificar se as tabelas existem e quantos registros existem ANTES da limpeza
SELECT 
    '=== CONTAGEM ANTES DA LIMPEZA ===' AS informacao,
    IFNULL((SELECT COUNT(*) FROM pedidos), 0) AS total_pedidos,
    IFNULL((SELECT COUNT(*) FROM itens_pedido), 0) AS total_itens_pedido,
    IFNULL((SELECT COUNT(*) FROM meios_pagamento_pedido), 0) AS total_meios_pagamento,
    IFNULL((SELECT COUNT(*) FROM sessoes_trabalho), 0) AS total_sessoes_trabalho;

-- Confirmar que outras tabelas serão preservadas
SELECT 
    '=== DADOS QUE SERÃO PRESERVADOS ===' AS informacao,
    IFNULL((SELECT COUNT(*) FROM produtos), 0) AS total_produtos,
    IFNULL((SELECT COUNT(*) FROM categorias), 0) AS total_categorias,
    IFNULL((SELECT COUNT(*) FROM clientes), 0) AS total_clientes,
    IFNULL((SELECT COUNT(*) FROM usuarios), 0) AS total_usuarios;

-- ============================================================
-- INÍCIO DA LIMPEZA
-- ============================================================
-- ATENÇÃO: A partir daqui os dados serão deletados!
-- 
-- O script usa uma transação, então se algo der errado,
-- você pode fazer ROLLBACK antes do COMMIT.
-- 
-- ORDEM DE EXCLUSÃO (importante devido às foreign keys):
-- 1. meios_pagamento_pedido (FK para pedidos com ON DELETE CASCADE)
-- 2. itens_pedido (FK para pedidos com ON DELETE CASCADE)
-- 3. pedidos (FK para sessoes_trabalho com ON DELETE RESTRICT)
-- 4. sessoes_trabalho (deve ser deletada após pedidos)
-- ============================================================

START TRANSACTION;

-- Desabilitar verificação de chaves estrangeiras temporariamente
-- (garante que a exclusão seja realizada mesmo com dependências)
-- NOTA: Mesmo com FOREIGN_KEY_CHECKS = 0, mantemos a ordem correta
--       para garantir consistência e facilitar manutenção futura
SET FOREIGN_KEY_CHECKS = 0;

-- 1. Deletar dados da tabela de meios de pagamento dos pedidos
-- Tabela: meios_pagamento_pedido
-- FK: pedido_id -> pedidos(id) com ON DELETE CASCADE
-- (Deletamos explicitamente por segurança e clareza)
DELETE FROM meios_pagamento_pedido;

-- 2. Deletar dados da tabela de itens de pedidos
-- Tabela: itens_pedido
-- FK: pedido_id -> pedidos(id) com ON DELETE CASCADE
-- (Deletamos explicitamente por segurança e clareza)
DELETE FROM itens_pedido;

-- 3. Deletar todos os pedidos
-- Tabela: pedidos
-- FK: sessao_id -> sessoes_trabalho(id) com ON DELETE RESTRICT
-- (Como a FK tem ON DELETE RESTRICT, precisamos deletar pedidos antes das sessões)
-- NOTA: As FKs de itens_pedido e meios_pagamento_pedido têm ON DELETE CASCADE,
--       então seriam deletados automaticamente, mas já deletamos explicitamente acima
DELETE FROM pedidos;

-- 4. Deletar todas as sessões de trabalho
-- Tabela: sessoes_trabalho
-- (A FK de pedidos para sessoes_trabalho tem ON DELETE RESTRICT,
--  por isso deletamos os pedidos primeiro antes de deletar as sessões)
DELETE FROM sessoes_trabalho;

-- Reabilitar verificação de chaves estrangeiras
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- COMMIT DA TRANSAÇÃO
-- ============================================================
-- Confirma todas as alterações
COMMIT;

-- Se algo der errado antes do commit, você pode descomentar a linha abaixo:
-- ROLLBACK;

-- ============================================================
-- VERIFICAÇÃO PÓS-LIMPEZA
-- ============================================================

-- Verificar quantos registros restaram
SELECT 
    '=== CONTAGEM APÓS A LIMPEZA ===' AS informacao,
    IFNULL((SELECT COUNT(*) FROM pedidos), 0) AS total_pedidos,
    IFNULL((SELECT COUNT(*) FROM itens_pedido), 0) AS total_itens_pedido,
    IFNULL((SELECT COUNT(*) FROM meios_pagamento_pedido), 0) AS total_meios_pagamento,
    IFNULL((SELECT COUNT(*) FROM sessoes_trabalho), 0) AS total_sessoes_trabalho;

-- Confirmar que outras tabelas foram preservadas
SELECT 
    '=== DADOS PRESERVADOS (VERIFICAÇÃO FINAL) ===' AS informacao,
    IFNULL((SELECT COUNT(*) FROM produtos), 0) AS total_produtos,
    IFNULL((SELECT COUNT(*) FROM categorias), 0) AS total_categorias,
    IFNULL((SELECT COUNT(*) FROM clientes), 0) AS total_clientes,
    IFNULL((SELECT COUNT(*) FROM usuarios), 0) AS total_usuarios;

-- ============================================================
-- FINALIZAÇÃO
-- ============================================================
SELECT '=== LIMPEZA CONCLUÍDA COM SUCESSO! ===' AS status;

