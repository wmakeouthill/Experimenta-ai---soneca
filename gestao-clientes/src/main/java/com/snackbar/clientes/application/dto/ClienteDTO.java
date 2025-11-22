package com.snackbar.clientes.application.dto;

import com.snackbar.clientes.domain.entities.Cliente;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteDTO {
    private String id;
    private String nome;
    private String telefone;
    private String email;
    private String cpf;
    private String observacoes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static ClienteDTO de(Cliente cliente) {
        return ClienteDTO.builder()
            .id(cliente.getId())
            .nome(cliente.getNome())
            .telefone(cliente.getTelefone())
            .email(cliente.getEmail())
            .cpf(cliente.getCpf())
            .observacoes(cliente.getObservacoes())
            .createdAt(cliente.getCreatedAt())
            .updatedAt(cliente.getUpdatedAt())
            .build();
    }
}

