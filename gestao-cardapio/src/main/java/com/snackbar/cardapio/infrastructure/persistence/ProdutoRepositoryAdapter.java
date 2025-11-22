package com.snackbar.cardapio.infrastructure.persistence;

import com.snackbar.cardapio.application.ports.ProdutoRepositoryPort;
import com.snackbar.cardapio.domain.entities.Produto;
import com.snackbar.cardapio.infrastructure.mappers.ProdutoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProdutoRepositoryAdapter implements ProdutoRepositoryPort {
    
    private final ProdutoJpaRepository jpaRepository;
    private final ProdutoMapper mapper;
    
    @Override
    public Produto salvar(Produto produto) {
        ProdutoEntity entity = mapper.paraEntity(produto);
        ProdutoEntity salvo = jpaRepository.save(entity);
        return mapper.paraDomain(salvo);
    }
    
    @Override
    public Optional<Produto> buscarPorId(String id) {
        return jpaRepository.findById(id)
            .map(mapper::paraDomain);
    }
    
    @Override
    public List<Produto> buscarTodos() {
        return jpaRepository.findAll().stream()
            .map(mapper::paraDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Produto> buscarPorCategoria(String categoria) {
        return jpaRepository.findByCategoria(categoria).stream()
            .map(mapper::paraDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Produto> buscarDisponiveis() {
        return jpaRepository.findByDisponivelTrue().stream()
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

