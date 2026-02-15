package com.snackbar.pedidos.infrastructure.persistence;

import com.snackbar.pedidos.application.ports.ObterNomeUsuarioPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Adapter que obtém nomes de usuários na tabela {@code usuarios} (mesmo schema do orquestrador).
 */
@Component
public class ObterNomeUsuarioAdapter implements ObterNomeUsuarioPort {

    private final JdbcTemplate jdbcTemplate;

    public ObterNomeUsuarioAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, String> obterNomesPorIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        String[] idArray = ids.stream().distinct().toArray(String[]::new);
        String placeholders = String.join(",", Stream.generate(() -> "?").limit(idArray.length).toList());
        String sql = "SELECT id, nome FROM usuarios WHERE id IN (" + placeholders + ")";

        Map<String, String> result = new HashMap<>();
        jdbcTemplate.query(sql, idArray, rs -> {
            result.put(rs.getString("id"), rs.getString("nome"));
        });
        return result;
    }
}
