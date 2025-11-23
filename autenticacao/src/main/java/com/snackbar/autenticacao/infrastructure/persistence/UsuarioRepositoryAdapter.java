package com.snackbar.autenticacao.infrastructure.persistence;

import com.snackbar.autenticacao.domain.entities.Usuario;
import com.snackbar.autenticacao.domain.ports.UsuarioRepositoryPort;
import com.snackbar.autenticacao.domain.valueobjects.Email;
import com.snackbar.autenticacao.infrastructure.mappers.UsuarioMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UsuarioRepositoryAdapter implements UsuarioRepositoryPort {
    
    private final UsuarioJpaRepository jpaRepository;
    private final UsuarioMapper mapper;
    
    @Override
    public Usuario salvar(Usuario usuario) {
        UsuarioEntity entity = mapper.paraEntity(usuario);
        UsuarioEntity salvo = jpaRepository.save(entity);
        return mapper.paraDomain(salvo);
    }
    
    @Override
    public Optional<Usuario> buscarPorId(String id) {
        return jpaRepository.findById(id)
            .map(mapper::paraDomain);
    }
    
    @Override
    public Optional<Usuario> buscarPorEmail(Email email) {
        return jpaRepository.findByEmail(email.getValor())
            .map(mapper::paraDomain);
    }
    
    @Override
    public Optional<Usuario> buscarPorEmailOuNome(String emailOuNome) {
        return jpaRepository.findByEmailOrNome(emailOuNome, emailOuNome)
            .map(mapper::paraDomain);
    }
    
    @Override
    public List<Usuario> buscarTodos() {
        return jpaRepository.findAll().stream()
            .map(mapper::paraDomain)
            .toList();
    }
    
    @Override
    public void excluir(String id) {
        jpaRepository.deleteById(id);
    }
    
    @Override
    public boolean existePorEmail(Email email) {
        return jpaRepository.existsByEmail(email.getValor());
    }
}

