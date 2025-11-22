package com.snackbar.clientes.application.ports;

import com.snackbar.clientes.domain.entities.Cliente;
import java.util.List;
import java.util.Optional;

public interface ClienteRepositoryPort {
    Cliente salvar(Cliente cliente);
    Optional<Cliente> buscarPorId(String id);
    List<Cliente> buscarTodos();
    List<Cliente> buscarPorTelefone(String telefone);
    List<Cliente> buscarPorNome(String nome);
}

