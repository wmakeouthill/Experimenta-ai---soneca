package com.snackbar.clientes.infrastructure.mappers;

import com.snackbar.clientes.domain.entities.Cliente;
import com.snackbar.clientes.infrastructure.persistence.ClienteEntity;
import org.springframework.stereotype.Component;

@Component
public class ClienteMapper {
    
    public ClienteEntity paraEntity(Cliente cliente) {
        return ClienteEntity.builder()
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
    
    public Cliente paraDomain(ClienteEntity entity) {
        Cliente cliente = Cliente.criar(
            entity.getNome(),
            entity.getTelefone(),
            entity.getEmail(),
            entity.getCpf(),
            entity.getObservacoes()
        );
        
        cliente.restaurarDoBanco(
            entity.getId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
        
        return cliente;
    }
}

