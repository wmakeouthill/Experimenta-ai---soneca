package com.snackbar.pedidos.infrastructure.gateway;

import com.snackbar.clientes.application.dto.ClienteDTO;
import com.snackbar.clientes.application.usecases.BuscarClientePorIdUseCase;
import com.snackbar.clientes.application.usecases.CadastrarClienteMesaUseCase;
import com.snackbar.clientes.application.usecases.ListarClientesUseCase;
import com.snackbar.pedidos.application.dto.ClientePublicoDTO;
import com.snackbar.pedidos.application.ports.ClienteGatewayPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Implementação do gateway de clientes para o módulo de pedidos.
 * Delega para os use cases do módulo gestao-clientes.
 */
@Component
@RequiredArgsConstructor
public class ClienteGatewayAdapter implements ClienteGatewayPort {

    private final ListarClientesUseCase listarClientesUseCase;
    private final CadastrarClienteMesaUseCase cadastrarClienteMesaUseCase;
    private final BuscarClientePorIdUseCase buscarClientePorIdUseCase;

    @Override
    public Optional<ClientePublicoDTO> buscarPorTelefone(String telefone) {
        List<ClienteDTO> clientes = listarClientesUseCase.executarPorTelefone(telefone);

        return clientes.stream()
                .filter(c -> c.getTelefone() != null &&
                        c.getTelefone().replaceAll("\\D", "").equals(telefone.replaceAll("\\D", "")))
                .findFirst()
                .map(this::toPublicoDTO);
    }

    @Override
    public ClientePublicoDTO cadastrar(String nome, String telefone, String senha) {
        ClienteDTO clienteCriado = cadastrarClienteMesaUseCase.executar(nome, telefone, senha);
        return toPublicoDTO(clienteCriado);
    }

    @Override
    public Optional<ClientePublicoDTO> buscarPorId(String id) {
        try {
            ClienteDTO cliente = buscarClientePorIdUseCase.executar(id);
            return Optional.of(toPublicoDTO(cliente));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private ClientePublicoDTO toPublicoDTO(ClienteDTO cliente) {
        return new ClientePublicoDTO(
                cliente.getId(),
                cliente.getNome(),
                cliente.getTelefone());
    }
}
