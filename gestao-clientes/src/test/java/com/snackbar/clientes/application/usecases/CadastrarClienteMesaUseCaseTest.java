package com.snackbar.clientes.application.usecases;

import com.snackbar.clientes.application.ports.ClienteRepositoryPort;
import com.snackbar.clientes.application.ports.ClienteSenhaServicePort;
import com.snackbar.clientes.domain.entities.Cliente;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CadastrarClienteMesaUseCaseTest {

    @Test
    void cadastraClienteComSenhaHasheada() {
        ClienteRepositoryPort repositorio = mock(ClienteRepositoryPort.class);
        ClienteSenhaServicePort senhas = mock(ClienteSenhaServicePort.class);
        when(senhas.hashSenha("senha123")).thenReturn("hash-seguro");
        when(repositorio.salvar(any(Cliente.class))).thenAnswer(chamada -> chamada.getArgument(0));
        var casoDeUso = new CadastrarClienteMesaUseCase(repositorio, senhas);

        var cliente = casoDeUso.executar("Ana", "21987654321", "senha123");

        assertTrue(cliente.isTemSenha());
        verify(repositorio).salvar(org.mockito.ArgumentMatchers.argThat(
                salvo -> "hash-seguro".equals(salvo.getSenhaHash())));
    }

    @Test
    void impedeCadastroDeTelefoneExistente() {
        ClienteRepositoryPort repositorio = mock(ClienteRepositoryPort.class);
        ClienteSenhaServicePort senhas = mock(ClienteSenhaServicePort.class);
        when(repositorio.existePorTelefone("21987654321")).thenReturn(true);
        var casoDeUso = new CadastrarClienteMesaUseCase(repositorio, senhas);

        var erro = assertThrows(IllegalArgumentException.class,
                () -> casoDeUso.executar("Ana", "21987654321", "senha123"));

        assertEquals("Telefone já cadastrado", erro.getMessage());
        verify(repositorio, never()).salvar(any(Cliente.class));
    }
}
