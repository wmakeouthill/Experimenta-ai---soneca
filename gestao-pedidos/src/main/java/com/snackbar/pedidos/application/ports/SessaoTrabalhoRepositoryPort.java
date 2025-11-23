package com.snackbar.pedidos.application.ports;

import com.snackbar.pedidos.domain.entities.SessaoTrabalho;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SessaoTrabalhoRepositoryPort {
    SessaoTrabalho salvar(SessaoTrabalho sessao);
    
    Optional<SessaoTrabalho> buscarPorId(String id);
    
    Optional<SessaoTrabalho> buscarSessaoAtiva();
    
    List<SessaoTrabalho> buscarPorDataInicio(LocalDate dataInicio);
    
    Optional<SessaoTrabalho> buscarUltimaSessaoPorData(LocalDate dataInicio);
    
    List<SessaoTrabalho> buscarTodas();
}

