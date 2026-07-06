-- ============================================================
-- Script: Seed de Cardápio para Testes Locais
-- Descrição: Insere categorias, produtos e adicionais de
--            lanchonete para desenvolvimento e testes
-- ============================================================
--
-- COMO EXECUTAR:
-- 1. Via Docker (desenvolvimento):
--    docker exec -i snackbar-mysql-dev mysql -usnackbar_user -p"SUA_SENHA" snackbar_db < seed-cardapio-local.sql
-- 2. Via IntelliJ / DataGrip: abra o arquivo e execute (Ctrl+Enter)
--
-- OBSERVAÇÕES:
-- - Idempotente: não insere se já existir categoria com o mesmo nome
-- - produtos.categoria armazena o NOME da categoria (string)
-- - Alguns produtos ficam indisponíveis para testar filtros
-- ============================================================

SET @agora = NOW();

-- ============================================================
-- 1. CATEGORIAS
-- ============================================================

INSERT INTO categorias (id, nome, descricao, ativa, created_at, updated_at)
SELECT * FROM (
    SELECT 'cat-0001-lanches'   AS id, 'Lanches'    AS nome, 'Sanduíches, hambúrgueres e lanches tradicionais' AS descricao, TRUE AS ativa, @agora AS created_at, @agora AS updated_at UNION ALL
    SELECT 'cat-0002-bebidas',   'Bebidas',    'Refrigerantes, sucos, água e milk-shakes' UNION ALL
    SELECT 'cat-0003-porcoes',   'Porções',    'Acompanhamentos e petiscos para compartilhar' UNION ALL
    SELECT 'cat-0004-sobremesas','Sobremesas', 'Doces e sobremesas da casa' UNION ALL
    SELECT 'cat-0005-combos',    'Combos',     'Promoções com lanche + bebida + acompanhamento' UNION ALL
    SELECT 'cat-0006-saladas',   'Saladas',    'Opções leves e saladas frescas'
) AS novas
WHERE NOT EXISTS (SELECT 1 FROM categorias c WHERE c.nome = novas.nome);

-- ============================================================
-- 2. PRODUTOS
-- ============================================================

INSERT INTO produtos (id, nome, descricao, preco, categoria, disponivel, foto, created_at, updated_at, version)
SELECT * FROM (
    -- Lanches
    SELECT 'prod-0001-xburger'     AS id, 'X-Burger'              AS nome, 'Hambúrguer bovino, queijo, alface, tomate e maionese' AS descricao, 18.90 AS preco, 'Lanches' AS categoria, TRUE  AS disponivel, NULL AS foto, @agora AS created_at, @agora AS updated_at, 0 AS version UNION ALL
    SELECT 'prod-0002-xbacon',      'X-Bacon',               'Hambúrguer bovino, bacon crocante, queijo e molho especial', 22.50, 'Lanches', TRUE,  NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0003-xsalada',     'X-Salada',              'Hambúrguer, queijo, alface, tomate, cebola e maionese', 19.90, 'Lanches', TRUE,  NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0004-xtudo',       'X-Tudo',                'Hambúrguer, bacon, ovo, presunto, queijo, salada e batata palha', 28.90, 'Lanches', TRUE,  NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0005-misto',       'Misto Quente',          'Pão, presunto e queijo gratinado na chapa', 12.90, 'Lanches', TRUE,  NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0006-bauru',       'Bauru',                 'Pão francês, rosbife, queijo, tomate e orégano', 16.50, 'Lanches', TRUE,  NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0007-americana',   'Americana',             'Pão, hambúrguer, ovo, presunto, queijo e batata palha', 24.90, 'Lanches', FALSE, NULL, @agora, @agora, 0 UNION ALL

    -- Bebidas
    SELECT 'prod-0008-coca-lata',   'Coca-Cola Lata 350ml',  'Refrigerante gelado', 6.00, 'Bebidas', TRUE,  NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0009-guarana',     'Guaraná Antarctica 350ml', 'Refrigerante de guaraná', 5.50, 'Bebidas', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0010-suco-laranja','Suco de Laranja Natural 500ml', 'Suco fresco da fruta', 9.90, 'Bebidas', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0011-agua',        'Água Mineral 500ml',    'Água sem gás', 4.00, 'Bebidas', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0012-milkshake',   'Milkshake Morango 400ml', 'Milkshake cremoso de morango', 14.90, 'Bebidas', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0013-cafe',        'Café Expresso',         'Café curto e encorpado', 5.00, 'Bebidas', FALSE, NULL, @agora, @agora, 0 UNION ALL

    -- Porções
    SELECT 'prod-0014-batata-p',    'Batata Frita Pequena',  'Porção individual com molho especial', 12.00, 'Porções', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0015-batata-g',    'Batata Frita Grande',   'Porção generosa para compartilhar', 22.00, 'Porções', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0016-frango',      'Frango a Passarinho',   'Coxinhas e sobrecoxas temperadas', 32.90, 'Porções', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0017-calabresa',   'Calabresa Acebolada',   'Linguiça calabresa fatiada com cebola', 29.90, 'Porções', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0018-mandioca',    'Mandioca Frita',        'Mandioca crocante com molho verde', 18.90, 'Porções', TRUE, NULL, @agora, @agora, 0 UNION ALL

    -- Sobremesas
    SELECT 'prod-0019-pudim',       'Pudim de Leite',        'Pudim caseiro com calda de caramelo', 10.90, 'Sobremesas', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0020-brigadeiro',  'Brigadeiro (unidade)',  'Brigadeiro gourmet enrolado no granulado', 4.50, 'Sobremesas', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0021-brownie',     'Brownie com Sorvete',   'Brownie quente com bola de sorvete de creme', 15.90, 'Sobremesas', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0022-acai',        'Açaí 300ml',            'Açaí com banana e granola', 16.90, 'Sobremesas', TRUE, NULL, @agora, @agora, 0 UNION ALL

    -- Combos
    SELECT 'prod-0023-combo-x',     'Combo X-Burger',        'X-Burger + batata pequena + refrigerante lata', 32.90, 'Combos', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0024-combo-kids',  'Combo Kids',            'Mini hambúrguer + batata kids + suco de caixinha', 24.90, 'Combos', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0025-combo-familia','Combo Família',        '2 X-Tudo + batata grande + 2 refrigerantes', 69.90, 'Combos', TRUE, NULL, @agora, @agora, 0 UNION ALL

    -- Saladas
    SELECT 'prod-0026-caesar',      'Salada Caesar',         'Alface americana, frango grelhado, croutons e molho caesar', 23.90, 'Saladas', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0027-mista',       'Salada Mista',          'Mix de folhas, tomate, cenoura e molho de iogurte', 19.90, 'Saladas', TRUE, NULL, @agora, @agora, 0 UNION ALL
    SELECT 'prod-0028-tropical',    'Salada Tropical',       'Folhas, manga, morango e molho de maracujá', 21.90, 'Saladas', FALSE, NULL, @agora, @agora, 0
) AS novos
WHERE NOT EXISTS (SELECT 1 FROM produtos p WHERE p.id = novos.id);

-- ============================================================
-- 3. ADICIONAIS (opcionais para lanches)
-- ============================================================

INSERT INTO adicionais (id, nome, descricao, preco, categoria, disponivel, created_at, updated_at)
SELECT * FROM (
    SELECT 'add-0001-bacon'    AS id, 'Bacon Extra'     AS nome, 'Fatias crocantes de bacon' AS descricao, 4.50 AS preco, 'Lanches' AS categoria, TRUE AS disponivel, @agora AS created_at, @agora AS updated_at UNION ALL
    SELECT 'add-0002-queijo',   'Queijo Extra',    'Fatia adicional de queijo cheddar', 3.00, 'Lanches', TRUE, @agora, @agora UNION ALL
    SELECT 'add-0003-ovo',      'Ovo Frito',       'Ovo frito no ponto', 2.50, 'Lanches', TRUE, @agora, @agora UNION ALL
    SELECT 'add-0004-catupiry', 'Catupiry',        'Recheio cremoso de catupiry', 3.50, 'Lanches', TRUE, @agora, @agora UNION ALL
    SELECT 'add-0005-cebola',   'Cebola Caramelizada', 'Cebola dourada na manteiga', 2.00, 'Lanches', TRUE, @agora, @agora UNION ALL
    SELECT 'add-0006-molho',    'Molho Especial',  'Molho da casa', 1.50, 'Lanches', TRUE, @agora, @agora
) AS novos
WHERE NOT EXISTS (SELECT 1 FROM adicionais a WHERE a.id = novos.id);

-- ============================================================
-- 4. ASSOCIAÇÃO PRODUTO-ADICIONAL (lanches com extras)
-- ============================================================

INSERT INTO produtos_adicionais (produto_id, adicional_id, created_at)
SELECT * FROM (
    SELECT 'prod-0001-xburger' AS produto_id, 'add-0001-bacon' AS adicional_id, @agora AS created_at UNION ALL
    SELECT 'prod-0001-xburger', 'add-0002-queijo', @agora UNION ALL
    SELECT 'prod-0001-xburger', 'add-0003-ovo', @agora UNION ALL
    SELECT 'prod-0002-xbacon', 'add-0002-queijo', @agora UNION ALL
    SELECT 'prod-0002-xbacon', 'add-0004-catupiry', @agora UNION ALL
    SELECT 'prod-0003-xsalada', 'add-0001-bacon', @agora UNION ALL
    SELECT 'prod-0004-xtudo', 'add-0005-cebola', @agora UNION ALL
    SELECT 'prod-0004-xtudo', 'add-0006-molho', @agora UNION ALL
    SELECT 'prod-0007-americana', 'add-0002-queijo', @agora
) AS novos
WHERE NOT EXISTS (
    SELECT 1 FROM produtos_adicionais pa
    WHERE pa.produto_id = novos.produto_id AND pa.adicional_id = novos.adicional_id
);

-- ============================================================
-- RESUMO
-- ============================================================

SELECT '=== SEED CARDÁPIO CONCLUÍDO ===' AS status;

SELECT categoria, COUNT(*) AS total, SUM(disponivel) AS disponiveis
FROM produtos
GROUP BY categoria
ORDER BY categoria;

SELECT
    (SELECT COUNT(*) FROM categorias) AS total_categorias,
    (SELECT COUNT(*) FROM produtos) AS total_produtos,
    (SELECT COUNT(*) FROM adicionais) AS total_adicionais,
    (SELECT COUNT(*) FROM produtos_adicionais) AS total_vinculos_adicionais;
