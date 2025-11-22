package com.snackbar.clientes.infrastructure.persistence;

import com.snackbar.clientes.application.ports.ClienteRepositoryPort;
import com.snackbar.clientes.domain.entities.Cliente;
import com.snackbar.clientes.infrastructure.mappers.ClienteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClienteRepositoryAdapter implements ClienteRepositoryPort {
    
    private final ClienteJpaRepository jpaRepository;
    private final ClienteMapper mapper;
    
    @Override
    public Cliente salvar(Cliente cliente) {
        ClienteEntity entity = mapper.paraEntity(cliente);
        ClienteEntity salvo = jpaRepository.save(entity);
        return mapper.paraDomain(salvo);
    }
    
    @Override
    public Optional<Cliente> buscarPorId(String id) {
        return jpaRepository.findById(id)
            .map(mapper::paraDomain);
    }
    
    @Override
    public List<Cliente> buscarTodos() {
        return jpaRepository.findAll().stream()
            .map(mapper::paraDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Cliente> buscarPorTelefone(String telefone) {
        return jpaRepository.findByTelefone(telefone).stream()
            .map(mapper::paraDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Cliente> buscarPorNome(String nome) {
        return jpaRepository.findByNomeContainingIgnoreCase(nome).stream()
            .map(mapper::paraDomain)
            .collect(Collectors.toList());
    }
}

