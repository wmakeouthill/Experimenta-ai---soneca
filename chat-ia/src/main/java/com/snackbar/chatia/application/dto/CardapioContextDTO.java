package com.snackbar.chatia.application.dto;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * DTO com contexto do cardápio para a IA.
 * Contém todas as informações necessárias para a IA responder sobre o cardápio.
 *
 * @param idsMaisPedidos     produtos mais vendidos, do mais para o menos vendido
 * @param idsMaisFavoritados produtos mais favoritados pelos clientes, do mais para o menos favoritado
 */
public record CardapioContextDTO(
    List<CategoriaContextDTO> categorias,
    List<ProdutoContextDTO> produtos,
    String resumoCardapio,
    List<String> idsMaisPedidos,
    List<String> idsMaisFavoritados
) {

    public static final Locale LOCALE_BR = Locale.forLanguageTag("pt-BR");

    /**
     * Categoria do cardápio.
     */
    public record CategoriaContextDTO(
        String id,
        String nome,
        String descricao,
        int ordem
    ) {}

    /**
     * Produto do cardápio com todas as informações relevantes.
     */
    public record ProdutoContextDTO(
        String id,
        String nome,
        String descricao,
        String categoria,
        BigDecimal preco,
        String imagemUrl,
        boolean disponivel,
        List<String> ingredientes,
        List<String> alergenos,
        boolean vegetariano,
        boolean vegano
    ) {}

    /** Preço no formato brasileiro (R$ 15,00), igual ao resto da tela. */
    public static String formatarPreco(BigDecimal preco) {
        return String.format(LOCALE_BR, "R$ %.2f", preco);
    }

    /**
     * Gera uma descrição textual do cardápio para o system prompt da IA.
     * Formato otimizado para que a IA entenda que SOMENTE estes produtos existem.
     */
    public String gerarDescricaoParaIA() {
        StringBuilder sb = new StringBuilder();

        // Cabeçalho enfático
        sb.append("╔══════════════════════════════════════════════════════════════════╗\n");
        sb.append("║              CARDÁPIO OFICIAL - LISTA COMPLETA                   ║\n");
        sb.append("║    ESTES SÃO OS ÚNICOS PRODUTOS QUE EXISTEM NO ESTABELECIMENTO   ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════════╝\n\n");

        if (resumoCardapio != null && !resumoCardapio.isBlank()) {
            sb.append("📋 ").append(resumoCardapio).append("\n\n");
        }

        int totalProdutos = 0;

        // Agrupa produtos por categoria
        for (CategoriaContextDTO categoria : categorias) {
            List<ProdutoContextDTO> produtosCategoria = produtos.stream()
                .filter(p -> p.categoria().equals(categoria.nome()) && p.disponivel())
                .toList();

            if (produtosCategoria.isEmpty()) continue;

            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("📁 CATEGORIA: ").append(categoria.nome().toUpperCase()).append("\n");
            if (categoria.descricao() != null && !categoria.descricao().isBlank()) {
                sb.append("   ").append(categoria.descricao()).append("\n");
            }
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

            for (ProdutoContextDTO produto : produtosCategoria) {
                totalProdutos++;
                sb.append("\n  🔹 PRODUTO: ").append(produto.nome()).append("\n");
                sb.append("     💰 PREÇO: ").append(formatarPreco(produto.preco())).append("\n");

                if (produto.descricao() != null && !produto.descricao().isBlank()) {
                    sb.append("     📝 Descrição: ").append(produto.descricao()).append("\n");
                }

                List<String> tags = new java.util.ArrayList<>();
                if (produto.vegetariano()) tags.add("🥬 Vegetariano");
                if (produto.vegano()) tags.add("🌱 Vegano");
                if (!tags.isEmpty()) {
                    sb.append("     ").append(String.join(" | ", tags)).append("\n");
                }
            }
            sb.append("\n");
        }

        // Rodapé enfático
        sb.append("╔══════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                    FIM DO CARDÁPIO                               ║\n");
        sb.append("║    Total de produtos disponíveis: ").append(String.format("%-3d", totalProdutos)).append("                           ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════╣\n");
        sb.append("║  ⚠️ ATENÇÃO: Qualquer produto NÃO listado acima NÃO EXISTE!     ║\n");
        sb.append("║  Use APENAS os nomes e preços EXATOS desta lista.               ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════════╝\n");

        anexarRanking(sb, "⭐ MAIS VENDIDOS DA CASA (do mais para o menos vendido)", idsMaisPedidos);
        anexarRanking(sb, "❤️ MAIS FAVORITADOS PELOS CLIENTES", idsMaisFavoritados);

        return sb.toString();
    }

    /** Só entra no ranking o que está disponível agora: a IA nunca recomenda item fora do cardápio. */
    private void anexarRanking(StringBuilder sb, String titulo, List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<ProdutoContextDTO> ranking = ids.stream()
            .flatMap(id -> produtos.stream().filter(p -> p.id().equals(id) && p.disponivel()).limit(1))
            .toList();
        if (ranking.isEmpty()) return;

        sb.append("\n").append(titulo).append(":\n");
        for (int i = 0; i < ranking.size(); i++) {
            ProdutoContextDTO produto = ranking.get(i);
            sb.append(i + 1).append(". ").append(produto.nome())
              .append(" — ").append(formatarPreco(produto.preco())).append("\n");
        }
    }

    /**
     * Produtos citados pelo nome em um texto (a resposta da IA), na ordem em que aparecem.
     * É o que faz os cards do chat acompanharem exatamente o que a IA listou.
     * Nomes mais longos casam primeiro, para "X-Bacon Duplo" não virar também um card de "X-Bacon";
     * e o nome precisa estar isolado, para "N°1" não casar dentro de "N°10".
     */
    public List<ProdutoContextDTO> produtosCitadosEm(String texto) {
        if (texto == null || texto.isBlank()) return List.of();

        StringBuilder normalizado = new StringBuilder(normalizarNome(texto));
        Map<Integer, ProdutoContextDTO> porPosicao = new TreeMap<>();
        List<ProdutoContextDTO> candidatos = produtos.stream()
            .filter(p -> p.disponivel() && p.nome() != null && !p.nome().isBlank())
            .sorted(Comparator.comparingInt((ProdutoContextDTO p) -> p.nome().length()).reversed())
            .toList();

        for (ProdutoContextDTO produto : candidatos) {
            Pattern nomeIsolado = Pattern.compile(
                "(?<![\\p{L}\\p{N}])" + Pattern.quote(normalizarNome(produto.nome())) + "(?![\\p{L}\\p{N}])");
            List<MatchResult> ocorrencias = nomeIsolado.matcher(normalizado).results().toList();
            if (ocorrencias.isEmpty()) continue;

            porPosicao.put(ocorrencias.get(0).start(), produto);
            // Apaga o trecho já reconhecido para um nome mais curto não casar dentro dele
            ocorrencias.forEach(o -> normalizado.replace(o.start(), o.end(), " ".repeat(o.end() - o.start())));
        }
        return List.copyOf(porPosicao.values());
    }

    /** Minúsculas e "º" (ordinal) igual a "°" (grau): a IA troca um pelo outro em "N°1". */
    private static String normalizarNome(String texto) {
        return texto.toLowerCase(Locale.ROOT).replace('º', '°');
    }
}
