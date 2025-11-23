package com.snackbar.pedidos.application.usecases;

import com.snackbar.pedidos.application.dto.SessaoTrabalhoDTO;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListarSessoesTrabalhoUseCase {
    
    private final SessaoTrabalhoRepositoryPort repository;
    
    public List<SessaoTrabalhoDTO> executar(LocalDate dataInicio) {
        if (dataInicio != null) {
            return repository.buscarPorDataInicio(dataInicio).stream()
                .map(SessaoTrabalhoDTO::de)
                .collect(Collectors.toList());
        }
        
        return repository.buscarTodas().stream()
            .map(SessaoTrabalhoDTO::de)
            .collect(Collectors.toList());
    }
}

