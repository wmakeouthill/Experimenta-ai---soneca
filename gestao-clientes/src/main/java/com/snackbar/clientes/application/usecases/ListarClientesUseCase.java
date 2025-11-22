package com.snackbar.clientes.application.usecases;

import com.snackbar.clientes.application.dto.ClienteDTO;
import com.snackbar.clientes.application.ports.ClienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListarClientesUseCase {
    
    private final ClienteRepositoryPort clienteRepository;
    
    public List<ClienteDTO> executar() {
        return clienteRepository.buscarTodos().stream()
            .map(ClienteDTO::de)
            .collect(Collectors.toList());
    }
    
    public List<ClienteDTO> executarPorTelefone(String telefone) {
        return clienteRepository.buscarPorTelefone(telefone).stream()
            .map(ClienteDTO::de)
            .collect(Collectors.toList());
    }
    
    public List<ClienteDTO> executarPorNome(String nome) {
        return clienteRepository.buscarPorNome(nome).stream()
            .map(ClienteDTO::de)
            .collect(Collectors.toList());
    }
}

