package com.snackbar.chatia.application.service;

import com.snackbar.chatia.application.dto.AcaoChatDTO;
import com.snackbar.chatia.application.dto.CardapioContextDTO;
import com.snackbar.chatia.application.dto.CardapioContextDTO.ProdutoContextDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço para detectar comandos de ação na mensagem do usuário.
 * Identifica quando o usuário quer adicionar um produto ao carrinho via texto.
 */
@Slf4j
@Service
public class DetectorComandoService {

    // Padrões para detectar comandos de adicionar ao carrinho
    private static final List<String> VERBOS_ADICIONAR = List.of(
        "adiciona", "adicione", "add", "coloca", "coloque", "bota", "boto", "bote",
        "quero", "vou querer", "me vê", "me ve", "me da", "me dá", "manda", "pede", "pedido",
        "inclui", "inclua", "põe", "poe", "pode adicionar", "pode colocar", "pode ser"
    );

    // Padrão PRIORITÁRIO: "numero X" ou "número X" (mais comum em menus)
    // Captura o número do produto quando mencionado explicitamente
    private static final Pattern PADRAO_NUMERO_PRODUTO = Pattern.compile(
        "(?:n[uú]mero|n°|nº)\\s*(\\d+)",
        Pattern.CASE_INSENSITIVE
    );
    
    // Padrão secundário: referências como "esse 4", "desse 4", "o 4"
    private static final Pattern PADRAO_REFERENCIA_DEMONSTRATIVO = Pattern.compile(
        "(?:d?ess[ea]|d?est[ea]|aquel[ea])\\s+(?:n[uú]mero|n°|nº)?\\s*(\\d+)",
        Pattern.CASE_INSENSITIVE
    );

    // Padrões para detectar quantidade EXPLÍCITA (com indicador de quantidade)
    // Ex: "2 unidades", "3x", "quero 2"
    private static final Pattern PADRAO_QUANTIDADE_EXPLICITA = Pattern.compile(
        "(\\d+)\\s*(?:unidade|unidades|x|un\\.?|vezes)\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    // Quantidade no início: "2 x-tudo", "3 hamburguer"
    private static final Pattern PADRAO_QUANTIDADE_INICIO = Pattern.compile(
        "^\\s*(\\d+)\\s+(?!numero|número|n°|nº)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Detecta se a mensagem contém um comando de adicionar ao carrinho.
     * 
     * @param mensagem Mensagem do usuário
     * @param cardapio Cardápio para identificar o produto
     * @return AcaoChatDTO com os dados do comando, ou acao.nenhuma() se não for comando
     */
    public AcaoChatDTO detectarComandoAdicionar(String mensagem, CardapioContextDTO cardapio) {
        if (mensagem == null || cardapio == null) {
            return AcaoChatDTO.nenhuma();
        }

        String mensagemNormalizada = normalizar(mensagem);
        
        // Verifica se contém verbo de adicionar
        boolean contemVerboAdicionar = VERBOS_ADICIONAR.stream()
            .anyMatch(verbo -> mensagemNormalizada.contains(normalizar(verbo)));
        
        if (!contemVerboAdicionar) {
            return AcaoChatDTO.nenhuma();
        }
        
        log.info("🎯 Comando de adicionar detectado na mensagem: '{}'", mensagem);
        
        // Tenta identificar qual produto (passa a mensagem original também para extrair número)
        Optional<ProdutoContextDTO> produtoEncontrado = identificarProduto(mensagemNormalizada, mensagem, cardapio);
        
        if (produtoEncontrado.isEmpty()) {
            log.warn("⚠️ Verbo de adicionar detectado, mas produto não identificado");
            return AcaoChatDTO.nenhuma();
        }
        
        ProdutoContextDTO produto = produtoEncontrado.get();
        
        // Extrai quantidade EXPLÍCITA (não confunde com número do produto)
        int quantidade = extrairQuantidadeExplicita(mensagem);
        
        // Extrai observação (ex: "sem cebola", "com bacon extra")
        String observacao = extrairObservacao(mensagem);
        
        log.info("✅ Comando identificado: adicionar '{}' x{} | obs: '{}'", 
                 produto.nome(), quantidade, observacao);
        
        return AcaoChatDTO.adicionarCarrinho(
            produto.id(),
            produto.nome(),
            quantidade,
            observacao
        );
    }

    /**
     * Identifica o produto mencionado na mensagem.
     * Prioriza: 1) Número do produto (ex: "numero 4"), 2) Nome exato, 3) Nome parcial
     */
    private Optional<ProdutoContextDTO> identificarProduto(String mensagemNormalizada, String mensagemOriginal, CardapioContextDTO cardapio) {
        List<ProdutoContextDTO> produtosDisponiveis = cardapio.produtos().stream()
            .filter(ProdutoContextDTO::disponivel)
            .toList();
        
        // 1. PRIMEIRO: Tenta match por "numero X" (padrão mais comum em menus)
        //    Ex: "quero 3 do numero 4" -> deve pegar "Número 4"
        Matcher matcherNumero = PADRAO_NUMERO_PRODUTO.matcher(mensagemOriginal);
        if (matcherNumero.find()) {
            try {
                int numeroReferencia = Integer.parseInt(matcherNumero.group(1));
                log.info("🔢 Referência 'numero {}' encontrada na mensagem", numeroReferencia);
                
                // Busca produto cujo nome contenha "Número X"
                for (ProdutoContextDTO produto : produtosDisponiveis) {
                    String nomeProduto = normalizar(produto.nome());
                    
                    if (nomeProduto.equals("numero " + numeroReferencia) ||
                        nomeProduto.equals("numero" + numeroReferencia) ||
                        nomeProduto.startsWith("numero " + numeroReferencia + " ") ||
                        nomeProduto.endsWith(" " + numeroReferencia) ||
                        nomeProduto.matches(".*\\bnumero\\s*" + numeroReferencia + "\\b.*")) {
                        log.info("📦 Produto identificado por 'numero {}': {}", numeroReferencia, produto.nome());
                        return Optional.of(produto);
                    }
                }
            } catch (NumberFormatException e) {
                // Ignora
            }
        }
        
        // 2. Tenta match por nome exato ou parcial (mas NÃO a palavra "numero" sozinha)
        for (ProdutoContextDTO produto : produtosDisponiveis) {
            String nomeProduto = normalizar(produto.nome());
            
            // Match exato
            if (mensagemNormalizada.contains(nomeProduto)) {
                log.info("📦 Produto identificado por nome exato: {}", produto.nome());
                return Optional.of(produto);
            }
            
            // Match parcial (ex: "x-tudo" para "X-Tudo do Soneca")
            String[] partes = nomeProduto.split("\\s+");
            for (String parte : partes) {
                // Ignora palavras muito curtas, genéricas, ou "numero" (já tratado acima)
                if (parte.length() > 3 && !isGenerico(parte) && !parte.equals("numero") && mensagemNormalizada.contains(parte)) {
                    log.info("📦 Produto identificado por nome parcial '{}': {}", parte, produto.nome());
                    return Optional.of(produto);
                }
            }
        }
        
        // 3. Tenta match por demonstrativo (ex: "desse 4", "esse 4")
        Matcher matcherDemo = PADRAO_REFERENCIA_DEMONSTRATIVO.matcher(mensagemOriginal);
        if (matcherDemo.find()) {
            try {
                int numeroReferencia = Integer.parseInt(matcherDemo.group(1));
                log.info("🔢 Referência demonstrativa '{}' encontrada", numeroReferencia);
                
                // Primeiro busca por nome
                for (ProdutoContextDTO produto : produtosDisponiveis) {
                    String nomeProduto = normalizar(produto.nome());
                    if (nomeProduto.contains("numero " + numeroReferencia) ||
                        nomeProduto.endsWith(" " + numeroReferencia)) {
                        log.info("📦 Produto identificado por demonstrativo: {}", produto.nome());
                        return Optional.of(produto);
                    }
                }
                
                // Fallback: usa como índice
                if (numeroReferencia >= 1 && numeroReferencia <= Math.min(20, produtosDisponiveis.size())) {
                    ProdutoContextDTO produto = produtosDisponiveis.get(numeroReferencia - 1);
                    log.info("📦 Produto identificado pelo índice {} (fallback): {}", numeroReferencia, produto.nome());
                    return Optional.of(produto);
                }
            } catch (NumberFormatException e) {
                // Ignora
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Verifica se uma palavra é genérica demais para match
     */
    private boolean isGenerico(String palavra) {
        return List.of("com", "sem", "para", "mais", "menos", "grande", "pequeno", "medio")
            .contains(palavra);
    }

    /**
     * Extrai a quantidade EXPLÍCITA da mensagem (default: 1).
     * Só considera quantidade quando há indicador claro.
     * NÃO confunde com referência a número de produto.
     */
    private int extrairQuantidadeExplicita(String mensagem) {
        String msgLower = mensagem.toLowerCase();
        
        // 1. Padrão explícito com unidade: "2 unidades", "3x", "2 un"
        Matcher matcherExplicito = PADRAO_QUANTIDADE_EXPLICITA.matcher(mensagem);
        if (matcherExplicito.find()) {
            try {
                int qtd = Integer.parseInt(matcherExplicito.group(1));
                log.info("🔢 Quantidade explícita com unidade: {}", qtd);
                return Math.min(Math.max(qtd, 1), 10);
            } catch (NumberFormatException e) {
                // Ignora
            }
        }
        
        // 2. Quantidade após verbo de adicionar: "quero 2", "me vê 3", "adiciona 2"
        //    Mas NÃO "quero o 4" ou "adiciona o número 4" (referência a produto)
        Pattern padraoAposVerbo = Pattern.compile(
            "(?:quero|adiciona|coloca|me\\s*v[eê]|me\\s*d[aá]|manda|pede)\\s+(\\d+)(?!\\s*(?:numero|número|n°|nº|o\\s|a\\s|do\\s|da\\s))",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcherAposVerbo = padraoAposVerbo.matcher(mensagem);
        if (matcherAposVerbo.find()) {
            try {
                int qtd = Integer.parseInt(matcherAposVerbo.group(1));
                log.info("🔢 Quantidade após verbo: {}", qtd);
                return Math.min(Math.max(qtd, 1), 10);
            } catch (NumberFormatException e) {
                // Ignora
            }
        }
        
        // 3. Quantidade antes de "do/da/de": "2 do número 4", "3 da coca"
        Pattern padraoAntesDe = Pattern.compile(
            "(\\d+)\\s+(?:do|da|de|del)\\s+",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcherAntesDe = padraoAntesDe.matcher(mensagem);
        if (matcherAntesDe.find()) {
            try {
                int qtd = Integer.parseInt(matcherAntesDe.group(1));
                log.info("🔢 Quantidade antes de 'do/da': {}", qtd);
                return Math.min(Math.max(qtd, 1), 10);
            } catch (NumberFormatException e) {
                // Ignora
            }
        }
        
        // 4. Quantidade no início seguida de produto (não número): "2 x-tudo", "3 hamburguer"
        Matcher matcherInicio = PADRAO_QUANTIDADE_INICIO.matcher(mensagem);
        if (matcherInicio.find()) {
            try {
                int qtd = Integer.parseInt(matcherInicio.group(1));
                log.info("🔢 Quantidade no início: {}", qtd);
                return Math.min(Math.max(qtd, 1), 10);
            } catch (NumberFormatException e) {
                // Ignora
            }
        }
        
        return 1; // Default
    }

    /**
     * Extrai observações da mensagem (ex: "sem cebola", "com bacon extra").
     */
    private String extrairObservacao(String mensagem) {
        StringBuilder observacoes = new StringBuilder();
        
        // Padrões de observação
        List<Pattern> padroes = List.of(
            Pattern.compile("(?:sem)\\s+([\\w\\s]+?)(?:,|\\.|e\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:com)\\s+(?:extra\\s+)?([\\w\\s]+?)(?:,|\\.|e\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:extra)\\s+([\\w\\s]+?)(?:,|\\.|e\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:tirar|tira|remove)\\s+(?:o|a)?\\s*([\\w\\s]+?)(?:,|\\.|e\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:mais|menos)\\s+([\\w\\s]+?)(?:,|\\.|e\\s|$)", Pattern.CASE_INSENSITIVE)
        );
        
        for (Pattern padrao : padroes) {
            Matcher matcher = padrao.matcher(mensagem);
            while (matcher.find()) {
                String obs = matcher.group(0).trim();
                if (!obs.isBlank()) {
                    if (observacoes.length() > 0) {
                        observacoes.append(", ");
                    }
                    observacoes.append(obs);
                }
            }
        }
        
        String resultado = observacoes.toString().trim();
        return resultado.isEmpty() ? null : resultado;
    }

    /**
     * Normaliza texto para comparação.
     */
    private String normalizar(String texto) {
        if (texto == null) return "";
        String normalizado = Normalizer.normalize(texto.toLowerCase().trim(), Normalizer.Form.NFD);
        return normalizado.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
