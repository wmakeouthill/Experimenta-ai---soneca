package com.snackbar.pedidos.application.ports;

import com.snackbar.pedidos.domain.entities.SessaoTrabalho;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SessaoTrabalhoRepositoryPort {
    SessaoTrabalho salvar(@NonNull SessaoTrabalho sessao);
    
    Optional<SessaoTrabalho> buscarPorId(@NonNull String id);
    
    Optional<SessaoTrabalho> buscarSessaoAtiva();
    
    List<SessaoTrabalho> buscarPorDataInicio(LocalDate dataInicio);
    
    Optional<SessaoTrabalho> buscarUltimaSessaoPorData(LocalDate dataInicio);
    
    List<SessaoTrabalho> buscarTodas();
}

