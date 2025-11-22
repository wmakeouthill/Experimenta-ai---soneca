package com.snackbar.cardapio.infrastructure.persistence;

import com.snackbar.cardapio.application.ports.CategoriaRepositoryPort;
import com.snackbar.cardapio.domain.entities.Categoria;
import com.snackbar.cardapio.infrastructure.mappers.CategoriaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CategoriaRepositoryAdapter implements CategoriaRepositoryPort {
    
    private final CategoriaJpaRepository jpaRepository;
    private final CategoriaMapper mapper;
    
    @Override
    public Categoria salvar(Categoria categoria) {
        CategoriaEntity entity = mapper.paraEntity(categoria);
        CategoriaEntity salva = jpaRepository.save(entity);
        return mapper.paraDomain(salva);
    }
    
    @Override
    public Optional<Categoria> buscarPorId(String id) {
        return jpaRepository.findById(id)
            .map(mapper::paraDomain);
    }
    
    @Override
    public List<Categoria> buscarTodas() {
        return jpaRepository.findAll().stream()
            .map(mapper::paraDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Categoria> buscarAtivas() {
        return jpaRepository.findByAtivaTrue().stream()
            .map(mapper::paraDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public void excluir(String id) {
        jpaRepository.deleteById(id);
    }
    
    @Override
    public boolean existePorId(String id) {
        return jpaRepository.existsById(id);
    }
}

