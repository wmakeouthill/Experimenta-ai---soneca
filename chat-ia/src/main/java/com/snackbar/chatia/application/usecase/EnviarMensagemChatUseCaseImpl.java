package com.snackbar.chatia.application.usecase;

import com.snackbar.chatia.application.dto.CardapioContextDTO;
import com.snackbar.chatia.application.dto.CardapioContextDTO.ProdutoContextDTO;
import com.snackbar.chatia.application.dto.ChatRequestDTO;
import com.snackbar.chatia.application.dto.ChatResponseDTO;
import com.snackbar.chatia.application.dto.ChatResponseDTO.ProdutoDestacadoDTO;
import com.snackbar.chatia.application.dto.HistoricoPedidosClienteContextDTO;
import com.snackbar.chatia.application.dto.AcaoChatDTO;
import com.snackbar.chatia.application.port.in.EnviarMensagemChatUseCase;
import com.snackbar.chatia.application.port.out.CardapioContextPort;
import com.snackbar.chatia.application.port.out.IAClientPort;
import com.snackbar.chatia.application.port.out.PedidosClienteContextPort;
import com.snackbar.chatia.application.service.DetectorComandoService;
import com.snackbar.chatia.domain.entity.MensagemChat;
import com.snackbar.chatia.domain.repository.HistoricoChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Caso de uso para enviar mensagens ao chat IA.
 * Orquestra a comunicação com a IA e gerenciamento do histórico.
 * Inclui contexto completo do cardápio (com mais vendidos e mais favoritados) e histórico do cliente;
 * os cards de produto saem dos produtos que a própria resposta da IA cita.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnviarMensagemChatUseCaseImpl implements EnviarMensagemChatUseCase {

    // Preço, disponibilidade e ranking mudam durante o expediente; o ranking varre os pedidos, então não a cada mensagem
    private static final Duration VALIDADE_CARDAPIO = Duration.ofMinutes(5);

    private final IAClientPort iaClient;
    private final HistoricoChatRepository historicoRepository;
    private final CardapioContextPort cardapioContextPort;
    private final PedidosClienteContextPort pedidosClienteContextPort;
    private final DetectorComandoService detectorComandoService;

    @Value("${chat.ia.nome-estabelecimento:Soneca Lanchonete}")
    private String nomeEstabelecimento;

    // Cache do cardápio; duas threads recarregando ao mesmo tempo só fazem a busca em dobro
    private volatile CardapioContextDTO cardapioCache;
    private volatile Instant cardapioCarregadoEm = Instant.MIN;
    
    @Override
    public ChatResponseDTO executar(ChatRequestDTO request) {
        String sessionId = request.sessionId();
        String mensagemUsuario = request.message();
        String clienteId = request.clienteId();
        
        log.info("Processando mensagem do chat - Session: {}, Cliente: {}", sessionId, clienteId);
        
        try {
            // Carrega cardápio (com cache simples)
            CardapioContextDTO cardapio = obterCardapio();
            
            // 🎯 PRIMEIRO: Verifica se é um comando de ação (adicionar, remover, limpar)
            AcaoChatDTO acaoDetectada = detectorComandoService.detectarComando(mensagemUsuario, cardapio);
            
            if (acaoDetectada.temAcao()) {
                // Adiciona mensagem do usuário ao histórico
                MensagemChat msgUsuario = MensagemChat.doUsuario(mensagemUsuario);
                historicoRepository.adicionarMensagem(sessionId, msgUsuario);
                
                // Gera resposta baseada no tipo de ação
                String resposta = gerarRespostaComando(acaoDetectada);
                
                // Adiciona resposta ao histórico
                MensagemChat msgAssistente = MensagemChat.doAssistente(resposta);
                historicoRepository.adicionarMensagem(sessionId, msgAssistente);
                
                log.info("🎯 Comando detectado: {} | produto: {} | qtd: {}", 
                         acaoDetectada.tipo(), acaoDetectada.produtoNome(), acaoDetectada.quantidade());
                
                return ChatResponseDTO.comAcao(resposta, acaoDetectada);
            }
            
            // Obtém histórico da sessão
            List<MensagemChat> historico = historicoRepository.obterHistorico(sessionId);
            
            // Constrói o system prompt com contexto completo
            String systemPromptCompleto = construirSystemPromptCompleto(clienteId, cardapio);

            // Adiciona mensagem do usuário ao histórico
            MensagemChat msgUsuario = MensagemChat.doUsuario(mensagemUsuario);
            historicoRepository.adicionarMensagem(sessionId, msgUsuario);

            // Chama a IA
            String respostaIA = iaClient.chat(systemPromptCompleto, historico, mensagemUsuario);

            // Adiciona resposta da IA ao histórico
            MensagemChat msgAssistente = MensagemChat.doAssistente(respostaIA);
            historicoRepository.adicionarMensagem(sessionId, msgAssistente);

            // Um card para cada produto que a resposta cita, na mesma ordem do texto
            List<ProdutoDestacadoDTO> produtosDestacados = cardapio == null ? List.of()
                : cardapio.produtosCitadosEm(respostaIA).stream()
                    .map(this::toProdutoDestacado)
                    .toList();
            
            log.info("✅ Resposta do chat gerada - Session: {}, Produtos encontrados: {}", 
                     sessionId, produtosDestacados.size());
            
            // Log detalhado dos produtos destacados
            if (!produtosDestacados.isEmpty()) {
                log.info("📦 Produtos destacados para o frontend:");
                produtosDestacados.forEach(p -> 
                    log.info("   - {} (ID: {}, R$ {}, disponivel: {})", 
                             p.nome(), p.id(), p.preco(), p.disponivel()));
            }
            
            return ChatResponseDTO.comProdutos(respostaIA, produtosDestacados);
            
        } catch (Exception e) {
            log.error("Erro ao processar mensagem do chat - Session: {}", sessionId, e);
            return ChatResponseDTO.erro("Desculpe, ocorreu um erro ao processar sua mensagem. Tente novamente.");
        }
    }
    
    /**
     * Gera uma resposta amigável baseada no tipo de comando detectado.
     */
    private String gerarRespostaComando(AcaoChatDTO acao) {
        return switch (acao.tipo()) {
            case ADICIONAR_CARRINHO -> gerarRespostaComandoAdicionar(acao);
            case REMOVER_CARRINHO -> gerarRespostaComandoRemover(acao);
            case LIMPAR_CARRINHO -> gerarRespostaComandoLimpar();
            case VER_CARRINHO -> gerarRespostaComandoVerCarrinho();
            default -> "Entendido! 😊";
        };
    }
    
    /**
     * Gera uma resposta amigável confirmando a adição ao carrinho.
     */
    private String gerarRespostaComandoAdicionar(AcaoChatDTO acao) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ótimo! Adicionei ");
        
        if (acao.quantidade() != null && acao.quantidade() > 1) {
            sb.append(acao.quantidade()).append("x ");
        }
        
        sb.append("**").append(acao.produtoNome()).append("** ao seu carrinho! 🛒");
        
        if (acao.observacao() != null && !acao.observacao().isBlank()) {
            sb.append("\n\n📝 Observação: *").append(acao.observacao()).append("*");
        }
        
        sb.append("\n\nDeseja mais alguma coisa? 😊");
        
        return sb.toString();
    }
    
    /**
     * Gera uma resposta amigável confirmando a remoção do carrinho.
     */
    private String gerarRespostaComandoRemover(AcaoChatDTO acao) {
        return "Pronto! Removi **" + acao.produtoNome() + "** do seu carrinho! 🗑️\n\nPosso ajudar com mais alguma coisa? 😊";
    }
    
    /**
     * Gera uma resposta amigável confirmando a limpeza do carrinho.
     */
    private String gerarRespostaComandoLimpar() {
        return "Carrinho limpo! 🗑️ Todos os itens foram removidos.\n\nQuer começar um novo pedido? Posso te ajudar a escolher! 😊";
    }
    
    /**
     * Gera uma resposta para ver o carrinho.
     * A resposta real com os itens será gerada pelo frontend, pois o carrinho está lá.
     */
    private String gerarRespostaComandoVerCarrinho() {
        return "Aqui está seu carrinho! 🛒";
    }
    
    // ============================================
    // CARDÁPIO E PRODUTOS
    // ============================================

    /**
     * Obtém o cardápio, recarregando quando o cache passa da validade.
     * Se a recarga falhar, segue com o último cardápio bom (ou null se nunca carregou).
     */
    private CardapioContextDTO obterCardapio() {
        if (cardapioCache != null && Instant.now().isBefore(cardapioCarregadoEm.plus(VALIDADE_CARDAPIO))) {
            return cardapioCache;
        }
        try {
            CardapioContextDTO carregado = cardapioContextPort.buscarCardapioParaIA();
            cardapioCache = carregado;
            cardapioCarregadoEm = Instant.now();
            log.info("✅ Cardápio carregado com sucesso: {} produtos em {} categorias",
                     carregado.produtos().size(), carregado.categorias().size());
        } catch (Exception e) {
            log.error("❌ ERRO ao carregar cardápio: {}", e.getMessage(), e);
        }
        return cardapioCache;
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

            QUANDO O CLIENTE PEDIR UMA LISTA (ex.: "quais opções de carne tem?", "que bebidas tem?"):
            - Liste TODOS os produtos do cardápio que atendem ao pedido, sem cortar nenhum
            - Um produto por linha, neste formato: - **Nome exato do produto** — R$ 0,00
            - Pode agrupar com uma linha curta antes de cada grupo (ex.: **Carne bovina:**)

            QUANDO O CLIENTE PEDIR RECOMENDAÇÃO ("o que você indica?", "o que é bom aqui?"):
            - Cliente identificado: priorize os favoritos e o histórico dele (seção do cliente abaixo)
            - Use também os "MAIS VENDIDOS DA CASA" e os "MAIS FAVORITADOS PELOS CLIENTES"
            - Diga o porquê em poucas palavras (ex.: "é o mais pedido da casa", "você sempre pede")
            - Recomende de 1 a 3 produtos, não o cardápio inteiro

            QUANDO O CLIENTE PEDIR UM PRODUTO QUE NÃO EXISTE:
            - Responda: "Desculpe, não temos [nome do produto] no nosso cardápio."
            - Sugira alternativas que EXISTAM no cardápio abaixo

            QUANDO O CLIENTE PERGUNTAR ALGO FORA DO ESCOPO:
            - Responda: "Só posso ajudar com informações sobre nosso cardápio e pedidos."

            NOMES E PREÇOS:
            - Escreva SEMPRE o nome do produto EXATAMENTE como está no cardápio (ex.: "N°1", não "Número 1").
              A tela mostra um card para cada produto citado pelo nome exato.
            - SEMPRE inclua o preço, no formato R$ 15,00

            FORMATO (a tela entende só este markdown):
            - **negrito** para nomes de produto
            - listas com "- " ou "1. ", um item por linha
            - parágrafos curtos separados por linha em branco
            - NÃO use tabelas, títulos (#), links, imagens nem blocos de código
            - Use emojis com moderação 😊🍔🥤
            - Seja direto; termine convidando a adicionar ao carrinho

            PROIBIDO:
            - Inventar produtos que não estão listados
            - Criar promoções ou combos imaginários
            - Mencionar preços diferentes dos listados
            - Falar sobre ingredientes que não estão descritos
            - Responder perguntas não relacionadas ao restaurante
            
            """.formatted(nomeEstabelecimento);
    }
}
