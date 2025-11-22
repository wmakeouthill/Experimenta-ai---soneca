package com.snackbar.clientes.domain.entities;

import com.snackbar.kernel.domain.entities.BaseEntity;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import lombok.Getter;

@Getter
public class Cliente extends BaseEntity {
    private String nome;
    private String telefone;
    private String email;
    private String cpf;
    private String observacoes;
    
    private Cliente() {
        super();
    }
    
    public static Cliente criar(String nome, String telefone, String email, String cpf, String observacoes) {
        validarDados(nome, telefone);
        
        Cliente cliente = new Cliente();
        cliente.nome = nome.trim();
        cliente.telefone = telefone != null ? telefone.trim() : null;
        cliente.email = email != null && !email.trim().isEmpty() ? email.trim() : null;
        cliente.cpf = cpf != null && !cpf.trim().isEmpty() ? cpf.trim() : null;
        cliente.observacoes = observacoes != null ? observacoes.trim() : null;
        cliente.touch();
        return cliente;
    }
    
    public void atualizarNome(String novoNome) {
        if (novoNome == null || novoNome.trim().isEmpty()) {
            throw new ValidationException("Nome do cliente não pode ser nulo ou vazio");
        }
        this.nome = novoNome.trim();
        touch();
    }
    
    public void atualizarTelefone(String novoTelefone) {
        this.telefone = novoTelefone != null ? novoTelefone.trim() : null;
        touch();
    }
    
    public void atualizarEmail(String novoEmail) {
        this.email = novoEmail != null && !novoEmail.trim().isEmpty() ? novoEmail.trim() : null;
        touch();
    }
    
    public void atualizarCpf(String novoCpf) {
        this.cpf = novoCpf != null && !novoCpf.trim().isEmpty() ? novoCpf.trim() : null;
        touch();
    }
    
    public void atualizarObservacoes(String novasObservacoes) {
        this.observacoes = novasObservacoes != null ? novasObservacoes.trim() : null;
        touch();
    }
    
    /**
     * Restaura o ID e timestamps do banco de dados (usado pelos mappers).
     */
    public void restaurarDoBanco(String id, java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt) {
        restaurarId(id);
        restaurarTimestamps(createdAt, updatedAt);
    }
    
    private static void validarDados(String nome, String telefone) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new ValidationException("Nome do cliente não pode ser nulo ou vazio");
        }
        if (telefone == null || telefone.trim().isEmpty()) {
            throw new ValidationException("Telefone do cliente não pode ser nulo ou vazio");
        }
    }
}

