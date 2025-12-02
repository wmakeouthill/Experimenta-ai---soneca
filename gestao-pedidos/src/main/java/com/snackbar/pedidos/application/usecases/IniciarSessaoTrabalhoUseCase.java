package com.snackbar.pedidos.application.usecases;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.SessaoTrabalhoDTO;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import com.snackbar.pedidos.domain.entities.SessaoTrabalho;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IniciarSessaoTrabalhoUseCase {
    
    private final SessaoTrabalhoRepositoryPort repository;
    
    public SessaoTrabalhoDTO executar(String usuarioId, BigDecimal valorAbertura) {
        validarNaoHaSessaoAtiva();
        
        Integer numeroSessao = calcularProximoNumeroSessao();
        SessaoTrabalho sessao = SessaoTrabalho.criar(numeroSessao, usuarioId, valorAbertura);
        @SuppressWarnings("null") // repository.salvar() nunca retorna null
        SessaoTrabalho sessaoSalva = repository.salvar(sessao);
        return SessaoTrabalhoDTO.de(sessaoSalva);
    }
    
    private void validarNaoHaSessaoAtiva() {
        Optional<SessaoTrabalho> sessaoAtiva = repository.buscarSessaoAtiva();
        if (sessaoAtiva.isPresent()) {
            throw new ValidationException("Já existe uma sessão ativa. Finalize ou pause a sessão atual antes de iniciar uma nova.");
        }
    }
    
    private Integer calcularProximoNumeroSessao() {
        LocalDate hoje = LocalDate.now();
        Optional<SessaoTrabalho> ultimaSessao = repository.buscarUltimaSessaoPorData(hoje);
        
        if (ultimaSessao.isEmpty()) {
            return 1;
        }
        
        return ultimaSessao.get().getNumeroSessao() + 1;
    }
}

