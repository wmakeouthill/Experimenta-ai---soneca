package com.snackbar.autenticacao.domain.ports;

import com.snackbar.autenticacao.domain.entities.Usuario;
import com.snackbar.autenticacao.domain.valueobjects.Email;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepositoryPort {
    Usuario salvar(Usuario usuario);
    Optional<Usuario> buscarPorId(String id);
    Optional<Usuario> buscarPorEmail(Email email);
    Optional<Usuario> buscarPorEmailOuNome(String emailOuNome);
    List<Usuario> buscarTodos();
    void excluir(String id);
    boolean existePorEmail(Email email);
}

