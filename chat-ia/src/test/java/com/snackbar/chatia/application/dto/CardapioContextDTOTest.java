package com.snackbar.chatia.application.dto;

import com.snackbar.chatia.application.dto.CardapioContextDTO.CategoriaContextDTO;
import com.snackbar.chatia.application.dto.CardapioContextDTO.ProdutoContextDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CardapioContextDTOTest {

    private static ProdutoContextDTO produto(String id, String nome, String preco, boolean disponivel) {
        return new ProdutoContextDTO(id, nome, null, "Hamburguers", new BigDecimal(preco), null,
            disponivel, List.of(), List.of(), false, false);
    }

    private static CardapioContextDTO cardapio(List<ProdutoContextDTO> produtos, List<String> maisPedidos) {
        return new CardapioContextDTO(
            List.of(new CategoriaContextDTO("c1", "Hamburguers", null, 0)),
            produtos, null, maisPedidos, List.of());
    }

    private static final List<ProdutoContextDTO> PRODUTOS = List.of(
        produto("1", "N°1", "15.00", true),
        produto("2", "N°2", "14.00", true),
        produto("5", "N°5", "30.00", true),
        produto("10", "N°10", "32.00", true),
        produto("xb", "X-Bacon", "20.00", true),
        produto("xbd", "X-Bacon Duplo", "28.00", true),
        produto("fora", "N°9", "18.00", false));

    @Test
    void deveGerarUmCardParaCadaProdutoListadoNaOrdemDoTexto() {
        // Arrange — resposta real da VPS, que antes gerava só o card do N°1
        String resposta = """
            **Carne bovina tradicional:**
            - **N°5** — R$ 30,00
            - **N°1** — R$ 15,00
            - **N°2** — R$ 14,00
            """;

        // Act
        List<ProdutoContextDTO> citados = cardapio(PRODUTOS, List.of()).produtosCitadosEm(resposta);

        // Assert
        assertThat(citados).extracting(ProdutoContextDTO::id).containsExactly("5", "1", "2");
    }

    @Test
    void naoDeveConfundirNomeCurtoDentroDeNomeMaisLongo() {
        // Act
        List<ProdutoContextDTO> citados = cardapio(PRODUTOS, List.of())
            .produtosCitadosEm("Temos o N°10 e o X-Bacon Duplo.");

        // Assert
        assertThat(citados).extracting(ProdutoContextDTO::id).containsExactly("10", "xbd");
    }

    @Test
    void deveAceitarOrdinalNoLugarDoGrauEIgnorarMaiusculas() {
        // Act
        List<ProdutoContextDTO> citados = cardapio(PRODUTOS, List.of())
            .produtosCitadosEm("Recomendo o nº1 e o x-bacon.");

        // Assert
        assertThat(citados).extracting(ProdutoContextDTO::id).containsExactly("1", "xb");
    }

    @Test
    void naoDeveGerarCardDeProdutoIndisponivel() {
        // Act
        List<ProdutoContextDTO> citados = cardapio(PRODUTOS, List.of()).produtosCitadosEm("O N°9 acabou.");

        // Assert
        assertThat(citados).isEmpty();
    }

    @Test
    void deveIncluirMaisVendidosDisponiveisComPrecoBrasileiro() {
        // Act
        String descricao = cardapio(PRODUTOS, List.of("5", "fora", "1")).gerarDescricaoParaIA();

        // Assert
        assertThat(descricao)
            .contains("MAIS VENDIDOS DA CASA")
            .contains("1. N°5 — R$ 30,00\n2. N°1 — R$ 15,00\n")
            .doesNotContain("N°9 —");
    }
}
