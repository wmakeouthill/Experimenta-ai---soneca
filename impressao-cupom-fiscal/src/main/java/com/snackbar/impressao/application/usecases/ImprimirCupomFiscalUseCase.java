package com.snackbar.impressao.application.usecases;

import com.snackbar.impressao.application.dtos.ImprimirCupomRequest;
import com.snackbar.impressao.application.dtos.ImprimirCupomResponse;
import com.snackbar.impressao.application.ports.ConfiguracaoImpressoraRepositoryPort;
import com.snackbar.impressao.application.ports.PedidoServicePort;
import com.snackbar.impressao.domain.entities.CupomFiscal;
import com.snackbar.impressao.domain.entities.TipoImpressora;
import com.snackbar.impressao.domain.ports.ImpressaoException;
import com.snackbar.impressao.domain.ports.ImpressoraPort;
import com.snackbar.impressao.domain.valueobjects.ConfiguracaoImpressora;
import com.snackbar.impressao.infrastructure.impressora.ImpressoraFactory;
import com.snackbar.pedidos.application.dto.ItemPedidoDTO;
import com.snackbar.pedidos.application.dto.MeioPagamentoDTO;
import com.snackbar.pedidos.application.dto.PedidoDTO;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.pedidos.domain.entities.StatusPedido;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImprimirCupomFiscalUseCase {
    
    private final PedidoServicePort pedidoService;
    private final ImpressoraFactory impressoraFactory;
    private final ConfiguracaoImpressoraRepositoryPort configuracaoRepository;
    
    public ImprimirCupomResponse executar(ImprimirCupomRequest request) {
        PedidoDTO pedido = buscarPedido(request.getPedidoId());
        ConfiguracaoImpressora configuracao = criarConfiguracao(request);
        CupomFiscal cupomFiscal = criarCupomFiscal(pedido, configuracao, request);
        
        try {
            ImpressoraPort impressora = impressoraFactory.criar(configuracao.getTipoImpressora());
            impressora.imprimir(cupomFiscal);
            
            return ImprimirCupomResponse.builder()
                    .sucesso(true)
                    .mensagem("Cupom fiscal impresso com sucesso")
                    .dataImpressao(LocalDateTime.now())
                    .pedidoId(pedido.getId())
                    .build();
        } catch (ImpressaoException e) {
            return ImprimirCupomResponse.builder()
                    .sucesso(false)
                    .mensagem("Erro ao imprimir cupom: " + e.getMessage())
                    .dataImpressao(LocalDateTime.now())
                    .pedidoId(pedido.getId())
                    .build();
        }
    }
    
    private PedidoDTO buscarPedido(String pedidoId) {
        if ("teste".equalsIgnoreCase(pedidoId)) {
            return criarPedidoTeste();
        }
        
        PedidoDTO pedido = pedidoService.buscarPedidoPorId(pedidoId);
        if (pedido == null) {
            throw new IllegalArgumentException("Pedido não encontrado: " + pedidoId);
        }
        return pedido;
    }
    
    private PedidoDTO criarPedidoTeste() {
        List<ItemPedidoDTO> itens = new ArrayList<>();
        itens.add(ItemPedidoDTO.builder()
                .produtoId("teste-1")
                .produtoNome("Hambúrguer Artesanal")
                .quantidade(2)
                .precoUnitario(new BigDecimal("25.00"))
                .subtotal(new BigDecimal("50.00"))
                .observacoes("Sem cebola")
                .build());
        itens.add(ItemPedidoDTO.builder()
                .produtoId("teste-2")
                .produtoNome("Batata Frita")
                .quantidade(1)
                .precoUnitario(new BigDecimal("12.00"))
                .subtotal(new BigDecimal("12.00"))
                .build());
        itens.add(ItemPedidoDTO.builder()
                .produtoId("teste-3")
                .produtoNome("Refrigerante")
                .quantidade(2)
                .precoUnitario(new BigDecimal("5.00"))
                .subtotal(new BigDecimal("10.00"))
                .build());
        
        List<MeioPagamentoDTO> meiosPagamento = new ArrayList<>();
        meiosPagamento.add(MeioPagamentoDTO.builder()
                .meioPagamento(MeioPagamento.PIX)
                .valor(new BigDecimal("50.00"))
                .build());
        meiosPagamento.add(MeioPagamentoDTO.builder()
                .meioPagamento(MeioPagamento.DINHEIRO)
                .valor(new BigDecimal("22.00"))
                .build());
        
        return PedidoDTO.builder()
                .id("teste")
                .numeroPedido("TESTE-001")
                .clienteId("teste-cliente")
                .clienteNome("Cliente de Teste")
                .status(StatusPedido.FINALIZADO)
                .itens(itens)
                .valorTotal(new BigDecimal("72.00"))
                .observacoes("Pedido de teste para impressão")
                .meiosPagamento(meiosPagamento)
                .usuarioId("teste-usuario")
                .dataPedido(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    private ConfiguracaoImpressora criarConfiguracao(ImprimirCupomRequest request) {
        String nomeImpressora = request.getNomeImpressora() != null 
                ? request.getNomeImpressora() 
                : request.getTipoImpressora().getDescricao();
        
        if (request.getTipoImpressora() == TipoImpressora.EPSON_TM_T20) {
            return ConfiguracaoImpressora.padraoEpson();
        } else if (request.getTipoImpressora() == TipoImpressora.DARUMA_800) {
            return ConfiguracaoImpressora.padraoDaruma();
        } else {
            return ConfiguracaoImpressora.criar(
                    request.getTipoImpressora(),
                    nomeImpressora,
                    80,
                    "UTF-8"
            );
        }
    }
    
    private CupomFiscal criarCupomFiscal(PedidoDTO pedido, ConfiguracaoImpressora configuracao, ImprimirCupomRequest request) {
        String nomeEstabelecimento = request.getNomeEstabelecimento() != null 
                ? request.getNomeEstabelecimento() 
                : "experimenta-ai-do-soneca";
        
        String logoBase64 = null;
        byte[] logoEscPos = null;
        
        var configSalva = configuracaoRepository.buscarAtiva();
        if (configSalva.isPresent()) {
            var config = configSalva.get();
            logoBase64 = config.getLogoBase64();
            logoEscPos = config.getLogoEscPos();
        }
        
        return CupomFiscal.criar(
                pedido,
                configuracao,
                nomeEstabelecimento,
                request.getEnderecoEstabelecimento(),
                request.getTelefoneEstabelecimento(),
                request.getCnpjEstabelecimento(),
                logoBase64,
                logoEscPos
        );
    }
}

