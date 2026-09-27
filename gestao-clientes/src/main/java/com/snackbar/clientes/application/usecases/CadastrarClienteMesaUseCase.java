package com.snackbar.clientes.application.usecases;

import com.snackbar.clientes.application.dto.ClienteDTO;
import com.snackbar.clientes.application.ports.ClienteRepositoryPort;
import com.snackbar.clientes.application.ports.ClienteSenhaServicePort;
import com.snackbar.clientes.domain.entities.Cliente;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CadastrarClienteMesaUseCase {

    private final ClienteRepositoryPort clienteRepository;
    private final ClienteSenhaServicePort senhaService;

    @Transactional
    public ClienteDTO executar(String nome, String telefone, String senha) {
        if (clienteRepository.existePorTelefone(telefone)) {
            throw new IllegalArgumentException("Telefone já cadastrado");
        }

        Cliente cliente = Cliente.criar(nome, telefone, null, null, null);
        cliente.definirSenha(senhaService.hashSenha(senha));
        return ClienteDTO.de(clienteRepository.salvar(cliente));
    }
}
