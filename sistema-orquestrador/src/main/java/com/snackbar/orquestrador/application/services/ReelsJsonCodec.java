package com.snackbar.orquestrador.application.services;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snackbar.orquestrador.application.dto.LobbyReelDTO;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReelsJsonCodec {

    private static final TypeReference<List<LobbyReelDTO>> TIPO = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public String escrever(List<LobbyReelDTO> reels) {
        try {
            return objectMapper.writeValueAsString(reels);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Reels promocionais inválidos", e);
        }
    }

    public List<LobbyReelDTO> ler(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<LobbyReelDTO> reels = objectMapper.readValue(json, TIPO);
            return reels == null ? Collections.emptyList() : reels;
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
