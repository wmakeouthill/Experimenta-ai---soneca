package com.snackbar.cardapio.application.usecases;

import com.snackbar.cardapio.application.dto.CategoriaDTO;
import com.snackbar.cardapio.application.ports.CategoriaRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListarCategoriasUseCase {
    
    private final CategoriaRepositoryPort categoriaRepository;
    
    public List<CategoriaDTO> executar() {
        return categoriaRepository.buscarTodas().stream()
            .map(CategoriaDTO::de)
            .collect(Collectors.toList());
    }
    
    public List<CategoriaDTO> executarAtivas() {
        return categoriaRepository.buscarAtivas().stream()
            .map(CategoriaDTO::de)
            .collect(Collectors.toList());
    }
}

