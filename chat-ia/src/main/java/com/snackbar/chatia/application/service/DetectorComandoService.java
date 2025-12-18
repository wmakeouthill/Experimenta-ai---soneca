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
        "quero", "vou querer", "me vê", "me da", "me dá", "manda", "pede", "pedido",
        "inclui", "inclua", "põe", "poe", "pode adicionar", "pode colocar"
    );

    // Padrões para detectar observações
    private static final Pattern PADRAO_OBSERVACAO = Pattern.compile(
        "(?:sem|com|extra|mais|menos|pouco|muito|tirar|adicionar|trocar)\\s+(.+?)(?:\\.|,|$)",
        Pattern.CASE_INSENSITIVE
    );

    // Padrões para detectar quantidade
    private static final Pattern PADRAO_QUANTIDADE = Pattern.compile(
        "(\\d+)\\s*(?:unidade|x|un)?",
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
        
        // Tenta identificar qual produto
        Optional<ProdutoContextDTO> produtoEncontrado = identificarProduto(mensagemNormalizada, cardapio);
        
        if (produtoEncontrado.isEmpty()) {
            log.warn("⚠️ Verbo de adicionar detectado, mas produto não identificado");
            return AcaoChatDTO.nenhuma();
        }
        
        ProdutoContextDTO produto = produtoEncontrado.get();
        
        // Extrai quantidade (default: 1)
        int quantidade = extrairQuantidade(mensagem);
        
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
     */
    private Optional<ProdutoContextDTO> identificarProduto(String mensagem, CardapioContextDTO cardapio) {
        // Primeiro tenta match exato ou parcial no nome
        for (ProdutoContextDTO produto : cardapio.produtos()) {
            if (!produto.disponivel()) continue;
            
            String nomeProduto = normalizar(produto.nome());
            
            // Match exato
            if (mensagem.contains(nomeProduto)) {
                return Optional.of(produto);
            }
            
            // Match parcial (ex: "x-tudo" para "X-Tudo do Soneca")
            String[] partes = nomeProduto.split("\\s+");
            for (String parte : partes) {
                if (parte.length() > 3 && mensagem.contains(parte)) {
                    return Optional.of(produto);
                }
            }
        }
        
        // Tenta match por número (ex: "o número 3", "numero 3")
        Pattern padraoPorNumero = Pattern.compile("(?:numero|número|n°|nº|n)\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher matcherNumero = padraoPorNumero.matcher(mensagem);
        if (matcherNumero.find()) {
            String numero = matcherNumero.group(1);
            for (ProdutoContextDTO produto : cardapio.produtos()) {
                String nomeProduto = normalizar(produto.nome());
                if (nomeProduto.contains("numero " + numero) || nomeProduto.equals(numero)) {
                    return Optional.of(produto);
                }
            }
        }
        
        return Optional.empty();
    }

    /**
     * Extrai a quantidade da mensagem (default: 1).
     */
    private int extrairQuantidade(String mensagem) {
        Matcher matcher = PADRAO_QUANTIDADE.matcher(mensagem);
        if (matcher.find()) {
            try {
                int qtd = Integer.parseInt(matcher.group(1));
                return Math.min(Math.max(qtd, 1), 10); // Entre 1 e 10
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        return 1;
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
