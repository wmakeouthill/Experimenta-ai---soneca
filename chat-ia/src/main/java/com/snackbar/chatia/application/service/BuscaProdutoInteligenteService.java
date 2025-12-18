package com.snackbar.chatia.application.service;

import com.snackbar.chatia.application.dto.CardapioContextDTO;
import com.snackbar.chatia.application.dto.CardapioContextDTO.ProdutoContextDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Serviço de busca inteligente de produtos com:
 * - Levenshtein Distance para tolerância a erros de digitação
 * - Stemming básico para português
 * - Busca por categoria
 * - Busca por ingredientes/descrição
 */
@Slf4j
@Service
public class BuscaProdutoInteligenteService {

    private static final int MAX_LEVENSHTEIN_DISTANCE = 3;
    private static final double MIN_SIMILARITY_SCORE = 0.6;
    private static final int MAX_RESULTADOS = 5;

    // Mapa de stemming básico para português (plural -> singular, variações comuns)
    private static final Map<String, String> STEMMING_MAP = Map.ofEntries(
        // Plurais comuns
        Map.entry("hamburgueres", "hamburguer"),
        Map.entry("hambúrgueres", "hamburguer"),
        Map.entry("hamburgers", "hamburguer"),
        Map.entry("burguers", "hamburguer"),
        Map.entry("lanches", "lanche"),
        Map.entry("batatas", "batata"),
        Map.entry("refrigerantes", "refrigerante"),
        Map.entry("sucos", "suco"),
        Map.entry("bebidas", "bebida"),
        Map.entry("sobremesas", "sobremesa"),
        Map.entry("doces", "doce"),
        Map.entry("salgados", "salgado"),
        Map.entry("combos", "combo"),
        Map.entry("pizzas", "pizza"),
        Map.entry("porções", "porção"),
        Map.entry("porcoes", "porção"),
        Map.entry("cervejas", "cerveja"),
        Map.entry("drinks", "drink"),
        Map.entry("açaís", "açaí"),
        Map.entry("acais", "açaí"),
        Map.entry("milk-shakes", "milkshake"),
        Map.entry("milkshakes", "milkshake"),
        Map.entry("sanduíches", "sanduíche"),
        Map.entry("sanduiches", "sanduiche"),
        Map.entry("sandwiches", "sanduiche"),
        Map.entry("hotdogs", "hotdog"),
        Map.entry("hot-dogs", "hotdog"),
        Map.entry("cachorro-quentes", "cachorro-quente"),
        Map.entry("cachorros-quentes", "cachorro-quente"),
        
        // Variações de escrita
        Map.entry("hamburguer", "hamburguer"),
        Map.entry("hambúrguer", "hamburguer"),
        Map.entry("burger", "hamburguer"),
        Map.entry("burguer", "hamburguer"),
        Map.entry("x-burguer", "x-burger"),
        Map.entry("xburguer", "x-burger"),
        Map.entry("cheese", "queijo"),
        Map.entry("bacon", "bacon"),
        Map.entry("fritas", "frita"),
        Map.entry("onion", "cebola"),
        Map.entry("rings", "ring")
    );

    // Sinônimos para busca
    private static final Map<String, List<String>> SINONIMOS = Map.of(
        "hamburguer", List.of("burger", "lanche", "sanduiche", "x-"),
        "batata", List.of("frita", "fritas", "chips"),
        "refrigerante", List.of("refri", "coca", "guarana", "fanta", "sprite"),
        "suco", List.of("natural", "laranja", "limão"),
        "cerveja", List.of("chopp", "beer", "gelada"),
        "açaí", List.of("acai", "açai")
    );

    /**
     * Busca produtos relevantes baseado na mensagem do usuário.
     * 
     * @param mensagem mensagem do usuário
     * @param cardapio cardápio completo
     * @return lista de produtos relevantes ordenados por relevância
     */
    public List<ProdutoContextDTO> buscarProdutosRelevantes(String mensagem, CardapioContextDTO cardapio) {
        if (mensagem == null || mensagem.isBlank() || cardapio == null || cardapio.produtos().isEmpty()) {
            return List.of();
        }

        String mensagemNormalizada = normalizar(mensagem);
        List<String> palavras = extrairPalavras(mensagemNormalizada);
        
        log.debug("Buscando produtos para mensagem: '{}' -> palavras: {}", mensagem, palavras);

        // Calcula score de relevância para cada produto
        Map<ProdutoContextDTO, Double> scores = new HashMap<>();
        
        for (ProdutoContextDTO produto : cardapio.produtos()) {
            if (!produto.disponivel()) continue;
            
            double score = calcularScoreRelevancia(produto, palavras, mensagemNormalizada);
            if (score > MIN_SIMILARITY_SCORE) {
                scores.put(produto, score);
            }
        }

        // Ordena por score e retorna os mais relevantes
        List<ProdutoContextDTO> resultados = scores.entrySet().stream()
            .sorted(Map.Entry.<ProdutoContextDTO, Double>comparingByValue().reversed())
            .limit(MAX_RESULTADOS)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        log.debug("Encontrados {} produtos relevantes", resultados.size());
        return resultados;
    }

    /**
     * Busca produtos por categoria.
     */
    public List<ProdutoContextDTO> buscarPorCategoria(String categoria, CardapioContextDTO cardapio) {
        if (categoria == null || cardapio == null) return List.of();
        
        String categoriaNormalizada = normalizar(categoria);
        String categoriaStemmed = aplicarStemming(categoriaNormalizada);
        
        return cardapio.produtos().stream()
            .filter(ProdutoContextDTO::disponivel)
            .filter(p -> {
                String catProduto = normalizar(p.categoria());
                return catProduto.contains(categoriaNormalizada) 
                    || catProduto.contains(categoriaStemmed)
                    || calcularSimilaridade(catProduto, categoriaNormalizada) > 0.7;
            })
            .limit(MAX_RESULTADOS)
            .collect(Collectors.toList());
    }

    /**
     * Busca produto específico por nome com fuzzy matching.
     */
    public Optional<ProdutoContextDTO> buscarProdutoPorNome(String nome, CardapioContextDTO cardapio) {
        if (nome == null || cardapio == null) return Optional.empty();
        
        String nomeNormalizado = normalizar(nome);
        String nomeStemmed = aplicarStemming(nomeNormalizado);
        
        return cardapio.produtos().stream()
            .filter(ProdutoContextDTO::disponivel)
            .max(Comparator.comparingDouble(p -> {
                String nomeProduto = normalizar(p.nome());
                double scoreExato = nomeProduto.contains(nomeNormalizado) ? 1.0 : 0.0;
                double scoreStemmed = nomeProduto.contains(nomeStemmed) ? 0.9 : 0.0;
                double scoreSimilaridade = calcularSimilaridade(nomeProduto, nomeNormalizado);
                return Math.max(Math.max(scoreExato, scoreStemmed), scoreSimilaridade);
            }))
            .filter(p -> {
                String nomeProduto = normalizar(p.nome());
                return nomeProduto.contains(nomeNormalizado) 
                    || nomeProduto.contains(nomeStemmed)
                    || calcularSimilaridade(nomeProduto, nomeNormalizado) > 0.7;
            });
    }

    /**
     * Identifica se a mensagem está pedindo uma categoria específica.
     */
    public Optional<String> identificarCategoriaMencionada(String mensagem, CardapioContextDTO cardapio) {
        String mensagemNormalizada = normalizar(mensagem);
        
        // Palavras que indicam busca por categoria
        List<String> indicadoresCategoria = List.of(
            "todos", "todas", "quais", "lista", "mostrar", "ver", "tem", "opcoes", "opções"
        );
        
        boolean querCategoria = indicadoresCategoria.stream()
            .anyMatch(mensagemNormalizada::contains);
        
        if (!querCategoria) return Optional.empty();
        
        // Busca categoria mencionada
        for (var categoria : cardapio.categorias()) {
            String catNormalizada = normalizar(categoria.nome());
            String catStemmed = aplicarStemming(catNormalizada);
            
            if (mensagemNormalizada.contains(catNormalizada) || mensagemNormalizada.contains(catStemmed)) {
                return Optional.of(categoria.nome());
            }
            
            // Verifica sinônimos da categoria
            for (var entry : SINONIMOS.entrySet()) {
                if (catNormalizada.contains(entry.getKey())) {
                    for (String sinonimo : entry.getValue()) {
                        if (mensagemNormalizada.contains(sinonimo)) {
                            return Optional.of(categoria.nome());
                        }
                    }
                }
            }
        }
        
        return Optional.empty();
    }

    // ==================== MÉTODOS AUXILIARES ====================

    /**
     * Calcula score de relevância de um produto para as palavras da busca.
     */
    private double calcularScoreRelevancia(ProdutoContextDTO produto, List<String> palavras, String mensagemCompleta) {
        String nomeProduto = normalizar(produto.nome());
        String descricaoProduto = normalizar(produto.descricao() != null ? produto.descricao() : "");
        String categoriaProduto = normalizar(produto.categoria());
        String textoCompleto = nomeProduto + " " + descricaoProduto + " " + categoriaProduto;
        
        double score = 0.0;
        
        for (String palavra : palavras) {
            String palavraStemmed = aplicarStemming(palavra);
            
            // Match exato no nome (peso maior)
            if (nomeProduto.contains(palavra) || nomeProduto.contains(palavraStemmed)) {
                score += 2.0;
            }
            
            // Match na descrição/ingredientes
            if (descricaoProduto.contains(palavra) || descricaoProduto.contains(palavraStemmed)) {
                score += 1.0;
            }
            
            // Match na categoria
            if (categoriaProduto.contains(palavra) || categoriaProduto.contains(palavraStemmed)) {
                score += 1.5;
            }
            
            // Fuzzy match com Levenshtein
            double melhorSimilaridade = 0.0;
            for (String palavraProduto : extrairPalavras(textoCompleto)) {
                double sim = calcularSimilaridade(palavraProduto, palavra);
                if (sim > melhorSimilaridade) {
                    melhorSimilaridade = sim;
                }
            }
            if (melhorSimilaridade > 0.7) {
                score += melhorSimilaridade;
            }
        }
        
        // Normaliza o score baseado no número de palavras
        return palavras.isEmpty() ? 0 : score / palavras.size();
    }

    /**
     * Normaliza texto: remove acentos, lowercase, remove caracteres especiais.
     */
    private String normalizar(String texto) {
        if (texto == null) return "";
        
        String normalizado = Normalizer.normalize(texto.toLowerCase().trim(), Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalizado).replaceAll("");
    }

    /**
     * Aplica stemming básico para português.
     */
    private String aplicarStemming(String palavra) {
        String stemmed = STEMMING_MAP.get(palavra);
        if (stemmed != null) return stemmed;
        
        // Regras básicas de stemming
        if (palavra.endsWith("es") && palavra.length() > 3) {
            return palavra.substring(0, palavra.length() - 2);
        }
        if (palavra.endsWith("s") && palavra.length() > 3) {
            return palavra.substring(0, palavra.length() - 1);
        }
        if (palavra.endsWith("ões") || palavra.endsWith("oes")) {
            return palavra.substring(0, palavra.length() - 3) + "ao";
        }
        
        return palavra;
    }

    /**
     * Extrai palavras significativas de um texto.
     */
    private List<String> extrairPalavras(String texto) {
        // Remove stop words comuns
        Set<String> stopWords = Set.of(
            "o", "a", "os", "as", "um", "uma", "uns", "umas", "de", "da", "do", "das", "dos",
            "em", "no", "na", "nos", "nas", "por", "para", "com", "sem", "e", "ou", "que",
            "tem", "ter", "quero", "quer", "queria", "gostaria", "me", "meu", "minha",
            "voce", "você", "favor", "por favor", "obrigado", "obrigada", "oi", "ola"
        );
        
        return Arrays.stream(texto.split("\\s+"))
            .map(this::normalizar)
            .filter(p -> p.length() > 2)
            .filter(p -> !stopWords.contains(p))
            .map(this::aplicarStemming)
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * Calcula similaridade entre duas strings usando Levenshtein normalizado.
     */
    private double calcularSimilaridade(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;
        
        int distancia = calcularLevenshtein(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        
        return 1.0 - ((double) distancia / maxLen);
    }

    /**
     * Calcula a distância de Levenshtein entre duas strings.
     */
    private int calcularLevenshtein(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s1.length()][s2.length()];
    }
}
