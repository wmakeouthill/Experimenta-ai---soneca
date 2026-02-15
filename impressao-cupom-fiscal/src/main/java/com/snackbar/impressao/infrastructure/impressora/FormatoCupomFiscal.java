package com.snackbar.impressao.infrastructure.impressora;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.snackbar.impressao.domain.entities.CupomFiscal;
import com.snackbar.pedidos.application.dto.ItemPedidoAdicionalDTO;
import com.snackbar.pedidos.application.dto.ItemPedidoDTO;
import com.snackbar.pedidos.application.dto.MeioPagamentoDTO;

public class FormatoCupomFiscal {

    private static final int LARGURA_PADRAO = 48;

    /**
     * Formata apenas o CONTEÚDO do cupom (sem comandos de impressora)
     * 
     * O Electron será responsável por adicionar comandos de inicialização,
     * finalização e corte específicos da impressora.
     * 
     * @param cupomFiscal - Dados do cupom fiscal
     * @return bytes ESC/POS contendo apenas o conteúdo formatado
     */
    public static byte[] formatarCupom(CupomFiscal cupomFiscal) {
        byte[] cupom = new byte[0];

        // 1. Cabeçalho (texto centralizado)
        // Logo é tratado separadamente pelo Electron via node-thermal-printer
        cupom = concatenar(cupom, EscPosComandos.alinharCentro());
        cupom = concatenar(cupom, formatarCabecalho(cupomFiscal));

        // 2. Conteúdo do cupom (dados do pedido)
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

        // NOTA: Comandos de inicialização (reset) e finalização (corte, feeds)
        // são adicionados pelo Electron conforme a impressora específica

        return cupom;
    }

    private static byte[] formatarCabecalho(CupomFiscal cupomFiscal) {
        byte[] cabecalho = new byte[0];

        // Nome do estabelecimento: altura dupla + negrito (mais evidente)
        cabecalho = concatenar(cabecalho, EscPosComandos.textoDuploAltura());
        String nome = cupomFiscal.getNomeEstabelecimento() + "\n";
        cabecalho = concatenar(cabecalho, nome.getBytes(StandardCharsets.UTF_8));

        // Volta para texto normal para o resto do cabeçalho
        cabecalho = concatenar(cabecalho, EscPosComandos.textoNormal());

        StringBuilder resto = new StringBuilder();

        if (cupomFiscal.getEnderecoEstabelecimento() != null
                && !cupomFiscal.getEnderecoEstabelecimento().trim().isEmpty()) {
            resto.append(cupomFiscal.getEnderecoEstabelecimento()).append("\n");
        }

        if (cupomFiscal.getTelefoneEstabelecimento() != null
                && !cupomFiscal.getTelefoneEstabelecimento().trim().isEmpty()) {
            resto.append("Tel: ").append(cupomFiscal.getTelefoneEstabelecimento()).append("\n");
        }

        if (cupomFiscal.getCnpjEstabelecimento() != null && !cupomFiscal.getCnpjEstabelecimento().trim().isEmpty()) {
            resto.append("CNPJ: ").append(formatarCnpj(cupomFiscal.getCnpjEstabelecimento())).append("\n");
        }

        resto.append("\n");

        // CUPOM FISCAL em negrito
        cabecalho = concatenar(cabecalho, resto.toString().getBytes(StandardCharsets.UTF_8));
        cabecalho = concatenar(cabecalho, EscPosComandos.textoNegrito());
        cabecalho = concatenar(cabecalho, "CUPOM FISCAL\n".getBytes(StandardCharsets.UTF_8));
        cabecalho = concatenar(cabecalho, EscPosComandos.textoNormal());
        cabecalho = concatenar(cabecalho, "\n".getBytes(StandardCharsets.UTF_8));

        return cabecalho;
    }

    private static byte[] formatarDadosPedido(CupomFiscal cupomFiscal) {
        StringBuilder dados = new StringBuilder();
        dados.append("Pedido: #").append(cupomFiscal.getPedido().getNumeroPedido()).append("\n");
        dados.append("Cliente: ").append(cupomFiscal.getPedido().getClienteNome()).append("\n");
        dados.append("Data: ").append(cupomFiscal.getDataFormatada()).append("\n");

        if (cupomFiscal.getPedido().getObservacoes() != null
                && !cupomFiscal.getPedido().getObservacoes().trim().isEmpty()) {
            dados.append("Obs: ").append(cupomFiscal.getPedido().getObservacoes()).append("\n");
        }

        dados.append("\n");

        return dados.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] formatarItens(List<ItemPedidoDTO> itens) {
        StringBuilder sb = new StringBuilder();
        sb.append("ITENS DO PEDIDO\n\n");

        int numeroItem = 1;
        for (ItemPedidoDTO item : itens) {
            String nome = item.getProdutoNome();
            String valor = String.format("R$ %.2f", item.getSubtotal().doubleValue());
            String prefixo = String.format("%d) ", numeroItem++);

            // Formato: "1) Nome Completo do Produto R$ 50.00"
            sb.append(formatarLinhaComValor(prefixo + nome, valor, LARGURA_PADRAO));
            sb.append("\n");

            // Detalhes de quantidade se qtd > 1
            if (item.getQuantidade() > 1) {
                String detalhe = String.format("   %dx R$ %.2f",
                        item.getQuantidade(),
                        item.getPrecoUnitario() != null ? item.getPrecoUnitario().doubleValue() : 0.0);
                sb.append(detalhe).append("\n");
            }

            // Adicionais do item
            if (item.getAdicionais() != null && !item.getAdicionais().isEmpty()) {
                for (ItemPedidoAdicionalDTO adicional : item.getAdicionais()) {
                    String textoAdicional = String.format("   + %s x%d",
                            adicional.getAdicionalNome(),
                            adicional.getQuantidade());
                    String valorAdicional = String.format("R$ %.2f",
                            adicional.getSubtotal() != null ? adicional.getSubtotal().doubleValue() : 0.0);
                    sb.append(formatarLinhaComValor(textoAdicional, valorAdicional, LARGURA_PADRAO));
                    sb.append("\n");
                }
            }

            // Observações do item
            if (item.getObservacoes() != null && !item.getObservacoes().trim().isEmpty()) {
                sb.append("   > ").append(item.getObservacoes()).append("\n");
            }
        }

        sb.append("\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
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
            String descricao = meioPagamento.getMeioPagamento().getDescricao();
            String valor = String.format("R$ %.2f", meioPagamento.getValor().doubleValue());
            pagamentos.append(formatarLinhaComValor(descricao, valor, LARGURA_PADRAO));
            pagamentos.append("\n");
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

    /**
     * Formata uma linha com texto à esquerda e valor à direita,
     * preenchendo o espaço entre eles com espaços.
     * Se o texto for longo demais, quebra em múltiplas linhas
     * e coloca o valor alinhado à direita na última linha.
     */
    private static String formatarLinhaComValor(String texto, String valor, int largura) {
        int espacoMinimo = 2; // mínimo de 2 espaços entre texto e valor
        int espacoDisponivel = largura - valor.length() - espacoMinimo;

        if (texto.length() <= espacoDisponivel) {
            // Cabe na mesma linha
            int espacos = largura - texto.length() - valor.length();
            return texto + " ".repeat(Math.max(1, espacos)) + valor;
        }

        // Texto longo: quebra em linhas e coloca valor na última
        StringBuilder resultado = new StringBuilder();
        String restante = texto;

        while (restante.length() > largura) {
            // Encontrar ponto de quebra (último espaço antes do limite)
            int pontoQuebra = restante.lastIndexOf(' ', largura - 1);
            if (pontoQuebra <= 0) {
                pontoQuebra = largura; // sem espaço, corta no limite
            }
            resultado.append(restante, 0, pontoQuebra).append("\n");
            restante = restante.substring(pontoQuebra).trim();
        }

        // Última linha: texto restante + valor alinhado à direita
        if (restante.length() + valor.length() + espacoMinimo <= largura) {
            int espacos = largura - restante.length() - valor.length();
            resultado.append(restante).append(" ".repeat(Math.max(1, espacos))).append(valor);
        } else {
            // Ainda não cabe, coloca valor em linha própria
            resultado.append(restante).append("\n");
            resultado.append(" ".repeat(Math.max(0, largura - valor.length()))).append(valor);
        }

        return resultado.toString();
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

    public static String formatarCupomLegivel(CupomFiscal cupomFiscal) {
        StringBuilder cupom = new StringBuilder();

        cupom.append("=".repeat(LARGURA_PADRAO)).append("\n");
        cupom.append(formatarCabecalhoLegivel(cupomFiscal));
        cupom.append("-".repeat(LARGURA_PADRAO)).append("\n");
        cupom.append(formatarDadosPedidoLegivel(cupomFiscal));
        cupom.append("-".repeat(LARGURA_PADRAO)).append("\n");
        cupom.append(formatarItensLegivel(cupomFiscal.getItens()));
        cupom.append("-".repeat(LARGURA_PADRAO)).append("\n");
        cupom.append(formatarTotalLegivel(cupomFiscal.getValorTotal()));
        cupom.append("-".repeat(LARGURA_PADRAO)).append("\n");
        cupom.append(formatarMeiosPagamentoLegivel(cupomFiscal.getMeiosPagamento()));
        cupom.append("-".repeat(LARGURA_PADRAO)).append("\n");
        cupom.append(formatarRodapeLegivel(cupomFiscal));
        cupom.append("\n\n");
        cupom.append("=".repeat(LARGURA_PADRAO)).append("\n");

        return cupom.toString();
    }

    private static String formatarCabecalhoLegivel(CupomFiscal cupomFiscal) {
        StringBuilder cabecalho = new StringBuilder();

        byte[] logoEscPos = obterLogoEscPos(cupomFiscal);
        if (logoEscPos != null && logoEscPos.length > 0) {
            int alturaLogo = calcularAlturaLogo(logoEscPos);
            cabecalho.append(centralizarTexto("┌─────────────────────────────┐", LARGURA_PADRAO)).append("\n");
            for (int i = 0; i < alturaLogo; i++) {
                cabecalho.append(centralizarTexto("│     [LOGO BITMAP AQUI]      │", LARGURA_PADRAO)).append("\n");
            }
            cabecalho.append(centralizarTexto("└─────────────────────────────┘", LARGURA_PADRAO)).append("\n");
            cabecalho.append(centralizarTexto("(Logo carregado do assets)", LARGURA_PADRAO)).append("\n");
            cabecalho.append("\n");
        } else {
            cabecalho.append(centralizarTexto(cupomFiscal.getNomeEstabelecimento(), LARGURA_PADRAO)).append("\n");
        }

        if (cupomFiscal.getEnderecoEstabelecimento() != null) {
            cabecalho.append(centralizarTexto(cupomFiscal.getEnderecoEstabelecimento(), LARGURA_PADRAO)).append("\n");
        }

        if (cupomFiscal.getTelefoneEstabelecimento() != null) {
            cabecalho.append(centralizarTexto("Tel: " + cupomFiscal.getTelefoneEstabelecimento(), LARGURA_PADRAO))
                    .append("\n");
        }

        if (cupomFiscal.getCnpjEstabelecimento() != null) {
            cabecalho.append(
                    centralizarTexto("CNPJ: " + formatarCnpj(cupomFiscal.getCnpjEstabelecimento()), LARGURA_PADRAO))
                    .append("\n");
        }

        cabecalho.append("\n");
        cabecalho.append(centralizarTexto("CUPOM FISCAL", LARGURA_PADRAO)).append("\n");
        cabecalho.append("\n");

        return cabecalho.toString();
    }

    private static String formatarDadosPedidoLegivel(CupomFiscal cupomFiscal) {
        StringBuilder dados = new StringBuilder();
        dados.append("Pedido: #").append(cupomFiscal.getPedido().getNumeroPedido()).append("\n");
        dados.append("Cliente: ").append(cupomFiscal.getPedido().getClienteNome()).append("\n");
        dados.append("Data: ").append(cupomFiscal.getDataFormatada()).append("\n");

        if (cupomFiscal.getPedido().getObservacoes() != null
                && !cupomFiscal.getPedido().getObservacoes().trim().isEmpty()) {
            dados.append("Obs: ").append(cupomFiscal.getPedido().getObservacoes()).append("\n");
        }

        dados.append("\n");

        return dados.toString();
    }

    private static String formatarItensLegivel(List<ItemPedidoDTO> itens) {
        StringBuilder sb = new StringBuilder();
        sb.append("ITENS DO PEDIDO\n\n");

        int numeroItem = 1;
        for (ItemPedidoDTO item : itens) {
            String nome = item.getProdutoNome();
            String valor = String.format("R$ %.2f", item.getSubtotal().doubleValue());
            String prefixo = String.format("%d) ", numeroItem++);

            sb.append(formatarLinhaComValor(prefixo + nome, valor, LARGURA_PADRAO));
            sb.append("\n");

            if (item.getQuantidade() > 1) {
                String detalhe = String.format("   %dx R$ %.2f",
                        item.getQuantidade(),
                        item.getPrecoUnitario() != null ? item.getPrecoUnitario().doubleValue() : 0.0);
                sb.append(detalhe).append("\n");
            }

            if (item.getAdicionais() != null && !item.getAdicionais().isEmpty()) {
                for (ItemPedidoAdicionalDTO adicional : item.getAdicionais()) {
                    String textoAdicional = String.format("   + %s x%d",
                            adicional.getAdicionalNome(),
                            adicional.getQuantidade());
                    String valorAdicional = String.format("R$ %.2f",
                            adicional.getSubtotal() != null ? adicional.getSubtotal().doubleValue() : 0.0);
                    sb.append(formatarLinhaComValor(textoAdicional, valorAdicional, LARGURA_PADRAO));
                    sb.append("\n");
                }
            }

            if (item.getObservacoes() != null && !item.getObservacoes().trim().isEmpty()) {
                sb.append("   > ").append(item.getObservacoes()).append("\n");
            }
        }

        return sb.toString();
    }

    private static String formatarTotalLegivel(BigDecimal valorTotal) {
        StringBuilder total = new StringBuilder();
        total.append("\n");
        total.append("TOTAL: R$ ").append(String.format("%.2f", valorTotal.doubleValue())).append("\n");

        return total.toString();
    }

    private static String formatarMeiosPagamentoLegivel(List<MeioPagamentoDTO> meiosPagamento) {
        StringBuilder pagamentos = new StringBuilder();
        pagamentos.append("FORMA DE PAGAMENTO:\n");

        for (MeioPagamentoDTO meioPagamento : meiosPagamento) {
            String descricao = meioPagamento.getMeioPagamento().getDescricao();
            String valor = String.format("R$ %.2f", meioPagamento.getValor().doubleValue());
            pagamentos.append(formatarLinhaComValor(descricao, valor, LARGURA_PADRAO));
            pagamentos.append("\n");
        }

        return pagamentos.toString();
    }

    private static String formatarRodapeLegivel(CupomFiscal cupomFiscal) {
        StringBuilder rodape = new StringBuilder();
        rodape.append("\n");
        rodape.append(centralizarTexto("Obrigado pela preferencia!", LARGURA_PADRAO)).append("\n");
        rodape.append(centralizarTexto("Volte sempre!", LARGURA_PADRAO)).append("\n");

        return rodape.toString();
    }

    private static String centralizarTexto(String texto, int largura) {
        if (texto == null) {
            return "";
        }
        int espacos = (largura - texto.length()) / 2;
        if (espacos <= 0) {
            return texto;
        }
        return " ".repeat(espacos) + texto;
    }

    private static byte[] obterLogoEscPos(CupomFiscal cupomFiscal) {
        if (cupomFiscal.getLogoEscPos() != null && cupomFiscal.getLogoEscPos().length > 0) {
            return cupomFiscal.getLogoEscPos();
        }

        return new byte[0];
    }

    private static int calcularAlturaLogo(byte[] logoEscPos) {
        if (logoEscPos == null || logoEscPos.length < 7) {
            return 0;
        }
        int alturaL = logoEscPos[5] & 0xFF;
        int alturaH = logoEscPos[6] & 0xFF;
        return alturaL | (alturaH << 8);
    }
}
