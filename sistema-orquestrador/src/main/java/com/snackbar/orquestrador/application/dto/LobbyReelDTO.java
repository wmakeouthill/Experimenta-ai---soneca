package com.snackbar.orquestrador.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LobbyReelDTO {
    private String id;
    private String titulo;
    private String videoUrl;
    private String imagemUrl;
}
