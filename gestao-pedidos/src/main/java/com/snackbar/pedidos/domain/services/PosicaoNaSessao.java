package com.snackbar.pedidos.domain.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Posição de chamada do pedido dentro da sessão de trabalho.
 * A meia-noite não reinicia a conta: a ordem é a data do pedido na mesma sessão.
 * Pedido cancelado continua ocupando a posição, para a nota já impressa não mudar.
 */
public final class PosicaoNaSessao {

    public record Marcador(String id, String sessaoId, LocalDateTime dataPedido, String numeroPedido) {
    }

    private static final Comparator<Marcador> ORDEM = Comparator
            .comparing(Marcador::dataPedido, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(marcador -> numeroInteiro(marcador.numeroPedido()));

    private PosicaoNaSessao() {
    }

    public static String formatar(int posicao) {
        return String.format("%02d", posicao);
    }

    public static Map<String, String> calcular(List<Marcador> marcadores) {
        Map<String, List<Marcador>> porSessao = new HashMap<>();
        for (Marcador marcador : marcadores) {
            if (marcador.id() == null || marcador.sessaoId() == null || marcador.sessaoId().isBlank()) {
                continue;
            }
            porSessao.computeIfAbsent(marcador.sessaoId(), chave -> new ArrayList<>()).add(marcador);
        }

        Map<String, String> posicoes = new HashMap<>();
        for (List<Marcador> grupo : porSessao.values()) {
            List<Marcador> ordenados = new ArrayList<>(grupo);
            ordenados.sort(ORDEM);
            for (int indice = 0; indice < ordenados.size(); indice++) {
                posicoes.put(ordenados.get(indice).id(), formatar(indice + 1));
            }
        }
        return Map.copyOf(posicoes);
    }

    private static int numeroInteiro(String numeroPedido) {
        if (numeroPedido == null || numeroPedido.isBlank()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(numeroPedido.trim());
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }
}
