package com.snackbar.pedidos.domain.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.snackbar.pedidos.domain.services.PosicaoNaSessao.Marcador;

class PosicaoNaSessaoTest {

    @Test
    void contaPorSessaoEContinuaDepoisDaMeiaNoite() {
        LocalDateTime antes = LocalDateTime.of(2026, 10, 2, 23, 50);
        LocalDateTime depois = LocalDateTime.of(2026, 10, 3, 0, 10);

        Map<String, String> posicoes = PosicaoNaSessao.calcular(List.of(
                new Marcador("b", "sessao-1", depois, "0107"),
                new Marcador("c", "sessao-2", depois, "0108"),
                new Marcador("a", "sessao-1", antes, "0106")));

        assertEquals("01", posicoes.get("a"));
        assertEquals("02", posicoes.get("b"));
        assertEquals("01", posicoes.get("c"));
    }

    @Test
    void desempataPeloNumeroGlobalQuandoADataEIgual() {
        LocalDateTime agora = LocalDateTime.of(2026, 10, 2, 12, 0);

        Map<String, String> posicoes = PosicaoNaSessao.calcular(List.of(
                new Marcador("segundo", "sessao-1", agora, "0002"),
                new Marcador("primeiro", "sessao-1", agora, "0001")));

        assertEquals("01", posicoes.get("primeiro"));
        assertEquals("02", posicoes.get("segundo"));
    }

    @Test
    void naoReaproveitaPosicaoEFormataAcimaDeNoventaENove() {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 2, 8, 0);
        Marcador primeiro = new Marcador("p1", "sessao-1", inicio, "0001");
        Marcador cancelado = new Marcador("p2", "sessao-1", inicio.plusMinutes(1), "0002");
        Marcador terceiro = new Marcador("p3", "sessao-1", inicio.plusMinutes(2), "0003");
        Marcador centesimo = new Marcador("p100", "sessao-1", inicio.plusMinutes(99), "0100");

        List<Marcador> marcadores = new ArrayList<>();
        marcadores.add(primeiro);
        marcadores.add(cancelado);
        marcadores.add(terceiro);
        for (int i = 4; i < 100; i++) {
            marcadores.add(new Marcador("p" + i, "sessao-1", inicio.plusMinutes(i), String.format("%04d", i)));
        }
        marcadores.add(centesimo);

        Map<String, String> posicoes = PosicaoNaSessao.calcular(marcadores);

        assertEquals("02", posicoes.get("p2"));
        assertEquals("03", posicoes.get("p3"));
        assertEquals("100", posicoes.get("p100"));
    }

    @Test
    void ignoraPedidoSemSessao() {
        Map<String, String> posicoes = PosicaoNaSessao.calcular(List.of(
                new Marcador("legado", "  ", LocalDateTime.of(2026, 10, 2, 12, 0), "0009")));

        assertFalse(posicoes.containsKey("legado"));
    }
}
