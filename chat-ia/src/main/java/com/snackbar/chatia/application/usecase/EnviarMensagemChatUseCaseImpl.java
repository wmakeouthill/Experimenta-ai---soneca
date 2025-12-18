package com.snackbar.chatia.application.usecase;

import com.snackbar.chatia.application.dto.CardapioContextDTO;
import com.snackbar.chatia.application.dto.CardapioContextDTO.ProdutoContextDTO;
import com.snackbar.chatia.application.dto.ChatRequestDTO;
import com.snackbar.chatia.application.dto.ChatResponseDTO;
import com.snackbar.chatia.application.dto.ChatResponseDTO.ProdutoDestacadoDTO;
import com.snackbar.chatia.application.dto.HistoricoPedidosClienteContextDTO;
import com.snackbar.chatia.application.port.in.EnviarMensagemChatUseCase;
import com.snackbar.chatia.application.port.out.CardapioContextPort;
import com.snackbar.chatia.application.port.out.IAClientPort;
import com.snackbar.chatia.application.port.out.PedidosClienteContextPort;
import com.snackbar.chatia.application.service.BuscaProdutoInteligenteService;
import com.snackbar.chatia.domain.entity.MensagemChat;
import com.snackbar.chatia.domain.repository.HistoricoChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Caso de uso para enviar mensagens ao chat IA.
 * Orquestra a comunicação com a IA e gerenciamento do histórico.
 * Inclui contexto completo do cardápio, histórico do cliente e busca inteligente de produtos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnviarMensagemChatUseCaseImpl implements EnviarMensagemChatUseCase {
    
    private final IAClientPort iaClient;
    private final HistoricoChatRepository historicoRepository;
    private final CardapioContextPort cardapioContextPort;
    private final PedidosClienteContextPort pedidosClienteContextPort;
    private final BuscaProdutoInteligenteService buscaProdutoService;
    
    @Value("${chat.ia.nome-estabelecimento:Soneca Lanchonete}")
    private String nomeEstabelecimento;
    
    // Cache do cardápio para evitar múltiplas chamadas
    private CardapioContextDTO cardapioCache;
    
    @Override
    public ChatResponseDTO executar(ChatRequestDTO request) {
        String sessionId = request.sessionId();
        String mensagemUsuario = request.message();
        String clienteId = request.clienteId();
        
        log.info("Processando mensagem do chat - Session: {}, Cliente: {}", sessionId, clienteId);
        
        try {
            // Carrega cardápio (com cache simples)
            CardapioContextDTO cardapio = obterCardapio();
            
            // Obtém histórico da sessão
            List<MensagemChat> historico = historicoRepository.obterHistorico(sessionId);
            
            // Constrói o system prompt com contexto completo
            String systemPromptCompleto = construirSystemPromptCompleto(clienteId, cardapio);
            
            // Busca produtos relevantes na mensagem do usuário usando Levenshtein/stemming
            List<ProdutoContextDTO> produtosEncontrados = buscarProdutosNaMensagem(mensagemUsuario, cardapio);
            
            // Adiciona contexto dos produtos encontrados ao prompt se houver
            String promptComProdutos = adicionarContextoProdutosEncontrados(systemPromptCompleto, produtosEncontrados);
            
            // Adiciona mensagem do usuário ao histórico
            MensagemChat msgUsuario = MensagemChat.doUsuario(mensagemUsuario);
            historicoRepository.adicionarMensagem(sessionId, msgUsuario);
            
            // Chama a IA
            String respostaIA = iaClient.chat(promptComProdutos, historico, mensagemUsuario);
            
            // Adiciona resposta da IA ao histórico
            MensagemChat msgAssistente = MensagemChat.doAssistente(respostaIA);
            historicoRepository.adicionarMensagem(sessionId, msgAssistente);
            
            // Converte produtos encontrados para DTOs de destaque
            List<ProdutoDestacadoDTO> produtosDestacados = produtosEncontrados.stream()
                .map(this::toProdutoDestacado)
                .toList();
            
            log.info("Resposta do chat gerada com sucesso - Session: {}, Produtos destacados: {}", 
                     sessionId, produtosDestacados.size());
            
            return ChatResponseDTO.comProdutos(respostaIA, produtosDestacados);
            
        } catch (Exception e) {
            log.error("Erro ao processar mensagem do chat - Session: {}", sessionId, e);
            return ChatResponseDTO.erro("Desculpe, ocorreu um erro ao processar sua mensagem. Tente novamente.");
        }
    }
    
    // ============================================
    // MÉTODOS DE BUSCA INTELIGENTE DE PRODUTOS
    // ============================================
    
    /**
     * Obtém o cardápio, usando cache para evitar múltiplas chamadas
     */
    private CardapioContextDTO obterCardapio() {
        if (cardapioCache == null) {
            try {
                cardapioCache = cardapioContextPort.buscarCardapioParaIA();
                log.info("✅ Cardápio carregado com sucesso: {} produtos em {} categorias", 
                         cardapioCache.produtos().size(), cardapioCache.categorias().size());
                
                // Log dos produtos para debug
                if (log.isDebugEnabled()) {
                    cardapioCache.produtos().forEach(p -> 
                        log.debug("  Produto: {} - R$ {}", p.nome(), p.preco()));
                }
            } catch (Exception e) {
                log.error("❌ ERRO ao carregar cardápio: {}", e.getMessage(), e);
                return null;
            }
        }
        return cardapioCache;
    }
    
    /**
     * Busca produtos relevantes na mensagem do usuário usando busca inteligente
     */
    private List<ProdutoContextDTO> buscarProdutosNaMensagem(String mensagem, CardapioContextDTO cardapio) {
        if (cardapio == null || cardapio.produtos().isEmpty()) {
            return List.of();
        }
        
        List<ProdutoContextDTO> produtosEncontrados = new java.util.ArrayList<>();
        
        // 1. Primeiro verifica se é uma busca por categoria
        Optional<String> categoriaMencionada = buscaProdutoService.identificarCategoriaMencionada(mensagem, cardapio);
        if (categoriaMencionada.isPresent()) {
            List<ProdutoContextDTO> produtosCategoria = buscaProdutoService.buscarPorCategoria(categoriaMencionada.get(), cardapio);
            produtosEncontrados.addAll(produtosCategoria);
            log.debug("Encontrados {} produtos na categoria '{}'", produtosCategoria.size(), categoriaMencionada.get());
        }
        
        // 2. Busca por produtos específicos mencionados
        List<ProdutoContextDTO> produtosRelevantes = buscaProdutoService.buscarProdutosRelevantes(mensagem, cardapio);
        for (ProdutoContextDTO produto : produtosRelevantes) {
            if (!produtosEncontrados.contains(produto)) {
                produtosEncontrados.add(produto);
            }
        }
        
        log.debug("Total de {} produtos encontrados para a mensagem", produtosEncontrados.size());
        return produtosEncontrados;
    }
    
    /**
     * Adiciona contexto dos produtos encontrados ao prompt para a IA
     */
    private String adicionarContextoProdutosEncontrados(String promptBase, List<ProdutoContextDTO> produtos) {
        if (produtos.isEmpty()) {
            return promptBase;
        }
        
        StringBuilder sb = new StringBuilder(promptBase);
        sb.append("\n\n=== PRODUTOS IDENTIFICADOS NA PERGUNTA DO CLIENTE ===\n");
        sb.append("Os seguintes produtos foram mencionados ou são relevantes para a pergunta:\n\n");
        
        for (ProdutoContextDTO produto : produtos) {
            sb.append("📌 ").append(produto.nome().toUpperCase()).append("\n");
            sb.append("   - ID: ").append(produto.id()).append("\n");
            sb.append("   - Preço: R$ ").append(String.format("%.2f", produto.preco())).append("\n");
            if (produto.categoria() != null) {
                sb.append("   - Categoria: ").append(produto.categoria()).append("\n");
            }
            if (produto.descricao() != null && !produto.descricao().isBlank()) {
                sb.append("   - Descrição: ").append(produto.descricao()).append("\n");
            }
            sb.append("\n");
        }
        
        sb.append("USE ESTES DADOS EXATOS ao responder sobre estes produtos!\n");
        sb.append("=== FIM DOS PRODUTOS IDENTIFICADOS ===\n");
        
        return sb.toString();
    }
    
    /**
     * Converte ProdutoContextDTO para ProdutoDestacadoDTO (interno do ChatResponseDTO)
     */
    private ProdutoDestacadoDTO toProdutoDestacado(ProdutoContextDTO produto) {
        return new ProdutoDestacadoDTO(
            produto.id(),
            produto.nome(),
            produto.descricao(),
            produto.categoria(),
            produto.preco(),
            produto.imagemUrl(),
            produto.disponivel()
        );
    }
    
    // ============================================
    // CONSTRUÇÃO DO SYSTEM PROMPT
    // ============================================
    
    /**
     * Constrói o system prompt completo com:
     * - Instruções de comportamento
     * - Cardápio completo do estabelecimento
     * - Histórico de pedidos do cliente (se identificado)
     */
    private String construirSystemPromptCompleto(String clienteId, CardapioContextDTO cardapio) {
        StringBuilder sb = new StringBuilder();
        
        // Instruções base do assistente
        sb.append(construirInstrucoesBase());
        sb.append("\n\n");
        
        // Contexto do cardápio
        if (cardapio != null) {
            String descricaoCardapio = cardapio.gerarDescricaoParaIA();
            sb.append(descricaoCardapio);
            sb.append("\n\n");
            log.debug("Cardápio incluído: {} categorias e {} produtos", 
                     cardapio.categorias().size(), cardapio.produtos().size());
        } else {
            sb.append("=== CARDÁPIO INDISPONÍVEL ===\n");
            sb.append("Não foi possível carregar o cardápio. Informe ao cliente que está indisponível no momento.\n\n");
        }
        
        // Contexto do cliente (se identificado)
        if (clienteId != null && !clienteId.isBlank()) {
            try {
                HistoricoPedidosClienteContextDTO historicoCliente = 
                    pedidosClienteContextPort.buscarHistoricoPedidosCliente(clienteId);
                sb.append(historicoCliente.gerarDescricaoParaIA());
                log.debug("Histórico do cliente {} carregado: {} pedidos", clienteId, historicoCliente.totalPedidos());
            } catch (Exception e) {
                log.warn("Erro ao carregar histórico do cliente: {}", e.getMessage());
            }
        }
        
        String promptFinal = sb.toString();
        log.debug("System prompt construído com {} caracteres", promptFinal.length());
        
        return promptFinal;
    }
    
    private String construirInstrucoesBase() {
        return """
            Você é o assistente virtual do %s.
            
            ╔══════════════════════════════════════════════════════════════════╗
            ║                    REGRAS ABSOLUTAS - LEIA COM ATENÇÃO           ║
            ╠══════════════════════════════════════════════════════════════════╣
            ║ 1. VOCÊ SÓ PODE FALAR SOBRE PRODUTOS QUE ESTÃO NO CARDÁPIO ABAIXO║
            ║ 2. SE O PRODUTO NÃO ESTÁ LISTADO = ELE NÃO EXISTE                ║
            ║ 3. NUNCA INVENTE NOMES DE PRODUTOS, PREÇOS OU DESCRIÇÕES         ║
            ║ 4. USE APENAS OS DADOS EXATOS FORNECIDOS NO CARDÁPIO             ║
            ╚══════════════════════════════════════════════════════════════════╝
            
            INSTRUÇÕES DE RESPOSTA:
            
            QUANDO O CLIENTE PERGUNTAR SOBRE O CARDÁPIO:
            - Liste APENAS os produtos que aparecem na seção "CARDÁPIO OFICIAL" abaixo
            - Use os nomes EXATOS dos produtos como estão escritos
            - Use os preços EXATOS (não arredonde, não invente)
            - Não mencione produtos que não estão na lista
            
            QUANDO O CLIENTE PEDIR UM PRODUTO QUE NÃO EXISTE:
            - Responda: "Desculpe, não temos [nome do produto] no nosso cardápio."
            - Sugira alternativas que EXISTAM no cardápio abaixo
            
            QUANDO O CLIENTE PERGUNTAR ALGO FORA DO ESCOPO:
            - Responda: "Só posso ajudar com informações sobre nosso cardápio e pedidos."
            
            FORMATO DAS RESPOSTAS:
            - Use emojis ocasionalmente 😊🍔🥤
            - Seja conciso e direto
            - SEMPRE inclua o preço quando mencionar um produto
            - Incentive adicionar itens ao carrinho
            
            PROIBIDO:
            - Inventar produtos que não estão listados
            - Criar promoções ou combos imaginários
            - Mencionar preços diferentes dos listados
            - Falar sobre ingredientes que não estão descritos
            - Responder perguntas não relacionadas ao restaurante
            
            """.formatted(nomeEstabelecimento);
    }
}
