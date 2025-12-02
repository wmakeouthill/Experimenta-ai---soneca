package com.snackbar.pedidos.infrastructure.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client para buscar informações de usuários.
 */
@Service
@Slf4j
public class UsuarioClient {
    
    // Cache simples de nomes de usuários para evitar chamadas repetidas
    private final Map<String, String> cacheNomes = new ConcurrentHashMap<>();
    
    /**
     * Busca o nome do usuário pelo ID.
     * Usa cache para evitar chamadas repetidas.
     */
    public String buscarNomePorId(String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) {
            return "Desconhecido";
        }
        
        // Verifica no cache primeiro
        return cacheNomes.computeIfAbsent(usuarioId, this::buscarNomeDoServico);
    }
    
    private String buscarNomeDoServico(String usuarioId) {
        // Por enquanto, retorna o ID formatado como fallback
        // Em uma implementação completa, faria uma chamada HTTP ao módulo de autenticação
        try {
            // Tenta extrair o email do ID se for um email
            if (usuarioId.contains("@")) {
                String nome = usuarioId.split("@")[0];
                return capitalizar(nome);
            }
            return "Usuário #" + usuarioId.substring(0, Math.min(8, usuarioId.length()));
        } catch (Exception e) {
            log.warn("Erro ao buscar nome do usuário {}: {}", usuarioId, e.getMessage());
            return "Desconhecido";
        }
    }
    
    private String capitalizar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        return texto.substring(0, 1).toUpperCase() + texto.substring(1).toLowerCase();
    }
}

