package com.snackbar.pedidos.domain.entities;

import com.snackbar.kernel.domain.entities.BaseEntity;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class SessaoTrabalho extends BaseEntity {
    private Integer numeroSessao;
    private LocalDate dataInicio;
    private LocalDateTime dataInicioCompleta;
    private LocalDateTime dataFim;
    private StatusSessao status;
    private String usuarioId;
    
    private SessaoTrabalho() {
        super();
        this.status = StatusSessao.ABERTA;
        this.dataInicioCompleta = LocalDateTime.now();
        this.dataInicio = LocalDate.now();
    }
    
    public static SessaoTrabalho criar(Integer numeroSessao, String usuarioId) {
        validarDados(numeroSessao, usuarioId);
        
        SessaoTrabalho sessao = new SessaoTrabalho();
        sessao.numeroSessao = numeroSessao;
        sessao.usuarioId = usuarioId;
        sessao.touch();
        return sessao;
    }
    
    public void pausar() {
        if (!status.podeSerPausada()) {
            throw new ValidationException("Não é possível pausar uma sessão com status " + status.getDescricao());
        }
        this.status = StatusSessao.PAUSADA;
        touch();
    }
    
    public void retomar() {
        if (!status.podeSerRetomada()) {
            throw new ValidationException("Não é possível retomar uma sessão com status " + status.getDescricao());
        }
        this.status = StatusSessao.ABERTA;
        touch();
    }
    
    public void finalizar() {
        if (!status.podeSerFinalizada()) {
            throw new ValidationException("Não é possível finalizar uma sessão com status " + status.getDescricao());
        }
        this.status = StatusSessao.FINALIZADA;
        this.dataFim = LocalDateTime.now();
        touch();
    }
    
    public boolean estaAtiva() {
        return status.estaAtiva();
    }
    
    public String obterNome() {
        return numeroSessao + " - " + dataInicio.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
    
    public void restaurarDoBanco(String id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        restaurarId(id);
        restaurarTimestamps(createdAt, updatedAt);
    }
    
    public void restaurarStatusDoBanco(StatusSessao status, LocalDateTime dataFim) {
        this.status = status;
        this.dataFim = dataFim;
    }
    
    /**
     * Restaura as datas de início do banco de dados (usado pelos mappers).
     * IMPORTANTE: Essas datas devem ser imutáveis após a criação da sessão.
     * 
     * @param dataInicio Data de início da sessão (apenas data, sem hora)
     * @param dataInicioCompleta Data e hora completa de início da sessão
     */
    public void restaurarDatasInicioDoBanco(LocalDate dataInicio, LocalDateTime dataInicioCompleta) {
        if (dataInicio == null) {
            throw new ValidationException("Data de início não pode ser nula");
        }
        if (dataInicioCompleta == null) {
            throw new ValidationException("Data de início completa não pode ser nula");
        }
        this.dataInicio = dataInicio;
        this.dataInicioCompleta = dataInicioCompleta;
    }
    
    private static void validarDados(Integer numeroSessao, String usuarioId) {
        if (numeroSessao == null || numeroSessao <= 0) {
            throw new ValidationException("Número da sessão deve ser maior que zero");
        }
        if (usuarioId == null || usuarioId.trim().isEmpty()) {
            throw new ValidationException("ID do usuário não pode ser nulo ou vazio");
        }
    }
}

