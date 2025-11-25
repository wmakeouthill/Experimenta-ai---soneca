package com.snackbar.impressao.infrastructure.impressora;

import com.snackbar.impressao.domain.entities.CupomFiscal;
import com.snackbar.pedidos.application.dto.ItemPedidoDTO;
import com.snackbar.pedidos.application.dto.MeioPagamentoDTO;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FormatoCupomFiscal {
    
    private static final int LARGURA_PADRAO = 48;
    
    public static byte[] formatarCupom(CupomFiscal cupomFiscal) {
        byte[] cupom = new byte[0];
        
        cupom = concatenar(cupom, EscPosComandos.inicializar());
        cupom = concatenar(cupom, EscPosComandos.alinharCentro());
        cupom = concatenar(cupom, EscPosComandos.textoDuplo());
        cupom = concatenar(cupom, formatarCabecalho(cupomFiscal));
        cupom = concatenar(cupom, EscPosComandos.textoNormal());
        cupom = concatenar(cupom, EscPosComandos.alinharEsquerda());
        cupom = concatenar(cupom, EscPosComandos.linhaSeparadora(LARGURA_PADRAO));
        cupom = concatenar(cupom, formatarDadosPedido(cupomFiscal));
        cupom = concatenar(cupom, EscPosComandos.linhaSeparadora(LARGURA_PADRAO));
        cupom = concatenar(cupom, formatarItens(cupomFiscal.getItens()));
        cupom = concatenar(cupom, EscPosComandos.linhaSeparadora(LARGURA_PADRAO));
        cupom = concatenar(cupom, formatarTotal(cupomFiscal.getValorTotal()));
        cupom = concatenar(cupom, EscPosComandos.linhaSeparadora(LARGURA_PADRAO));
        cupom = concatenar(cupom, formatarMeiosPagamento(cupomFiscal.getMeiosPagamento()));
        cupom = concatenar(cupom, EscPosComandos.linhaSeparadora(LARGURA_PADRAO));
        cupom = concatenar(cupom, formatarRodape(cupomFiscal));
        cupom = concatenar(cupom, EscPosComandos.linhaEmBranco(2));
        cupom = concatenar(cupom, EscPosComandos.cortarPapel());
        
        return cupom;
    }
    
    private static byte[] formatarCabecalho(CupomFiscal cupomFiscal) {
        StringBuilder cabecalho = new StringBuilder();
        cabecalho.append(cupomFiscal.getNomeEstabelecimento()).append("\n");
        
        if (cupomFiscal.getEnderecoEstabelecimento() != null) {
            cabecalho.append(cupomFiscal.getEnderecoEstabelecimento()).append("\n");
        }
        
        if (cupomFiscal.getTelefoneEstabelecimento() != null) {
            cabecalho.append("Tel: ").append(cupomFiscal.getTelefoneEstabelecimento()).append("\n");
        }
        
        if (cupomFiscal.getCnpjEstabelecimento() != null) {
            cabecalho.append("CNPJ: ").append(formatarCnpj(cupomFiscal.getCnpjEstabelecimento())).append("\n");
        }
        
        cabecalho.append("\n");
        cabecalho.append("CUPOM FISCAL").append("\n");
        cabecalho.append("\n");
        
        return cabecalho.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    private static byte[] formatarDadosPedido(CupomFiscal cupomFiscal) {
        StringBuilder dados = new StringBuilder();
        dados.append("Pedido: #").append(cupomFiscal.getPedido().getNumeroPedido()).append("\n");
        dados.append("Cliente: ").append(cupomFiscal.getPedido().getClienteNome()).append("\n");
        dados.append("Data: ").append(cupomFiscal.getDataFormatada()).append("\n");
        
        if (cupomFiscal.getPedido().getObservacoes() != null && !cupomFiscal.getPedido().getObservacoes().trim().isEmpty()) {
            dados.append("Obs: ").append(cupomFiscal.getPedido().getObservacoes()).append("\n");
        }
        
        dados.append("\n");
        
        return dados.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    private static byte[] formatarItens(List<ItemPedidoDTO> itens) {
        StringBuilder itensFormatados = new StringBuilder();
        itensFormatados.append("ITEM | DESCRICAO | QTD | VALOR\n");
        
        int numeroItem = 1;
        for (ItemPedidoDTO item : itens) {
            String nome = truncarTexto(item.getProdutoNome(), 20);
            String linha = String.format("%-4d | %-20s | %-3d | R$ %7.2f\n",
                    numeroItem++,
                    nome,
                    item.getQuantidade(),
                    item.getSubtotal().doubleValue());
            
            itensFormatados.append(linha);
            
            if (item.getObservacoes() != null && !item.getObservacoes().trim().isEmpty()) {
                itensFormatados.append("     * ").append(item.getObservacoes()).append("\n");
            }
        }
        
        return itensFormatados.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    private static byte[] formatarTotal(BigDecimal valorTotal) {
        StringBuilder total = new StringBuilder();
        total.append("\n");
        total.append("TOTAL: R$ ").append(String.format("%.2f", valorTotal.doubleValue())).append("\n");
        
        return total.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    private static byte[] formatarMeiosPagamento(List<MeioPagamentoDTO> meiosPagamento) {
        StringBuilder pagamentos = new StringBuilder();
        pagamentos.append("FORMA DE PAGAMENTO:\n");
        
        for (MeioPagamentoDTO meioPagamento : meiosPagamento) {
            pagamentos.append(String.format("%-20s R$ %7.2f\n",
                    meioPagamento.getMeioPagamento().getDescricao(),
                    meioPagamento.getValor().doubleValue()));
        }
        
        return pagamentos.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    private static byte[] formatarRodape(CupomFiscal cupomFiscal) {
        StringBuilder rodape = new StringBuilder();
        rodape.append("\n");
        rodape.append("Obrigado pela preferencia!\n");
        rodape.append("Volte sempre!\n");
        
        return rodape.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    private static String formatarCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) {
            return cnpj;
        }
        return String.format("%s.%s.%s/%s-%s",
                cnpj.substring(0, 2),
                cnpj.substring(2, 5),
                cnpj.substring(5, 8),
                cnpj.substring(8, 12),
                cnpj.substring(12, 14));
    }
    
    private static String truncarTexto(String texto, int tamanhoMaximo) {
        if (texto == null) {
            return "";
        }
        if (texto.length() <= tamanhoMaximo) {
            return texto;
        }
        return texto.substring(0, tamanhoMaximo - 3) + "...";
    }
    
    private static byte[] concatenar(byte[] array1, byte[] array2) {
        byte[] resultado = new byte[array1.length + array2.length];
        System.arraycopy(array1, 0, resultado, 0, array1.length);
        System.arraycopy(array2, 0, resultado, array1.length, array2.length);
        return resultado;
    }
}

