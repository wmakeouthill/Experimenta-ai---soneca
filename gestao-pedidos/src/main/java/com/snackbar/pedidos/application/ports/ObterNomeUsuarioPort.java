package com.snackbar.pedidos.application.ports;

import java.util.Collection;
import java.util.Map;

/**
 * Port para obter nomes de usuários por ID (ex.: para exibir nos cards de sessões de trabalho).
 */
public interface ObterNomeUsuarioPort {

    /**
     * Retorna mapa id → nome para os IDs informados.
     * IDs não encontrados podem ficar de fora do mapa ou mapear para o próprio id.
     */
    Map<String, String> obterNomesPorIds(Collection<String> ids);
}
