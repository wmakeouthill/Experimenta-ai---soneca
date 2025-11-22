package com.snackbar.pedidos.domain.entities;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.entities.BaseEntity;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.domain.valueobjects.NumeroPedido;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
public class Pedido extends BaseEntity {
    private NumeroPedido numeroPedido;
    private String clienteId;
    private String clienteNome;
    private StatusPedido status;
    private List<ItemPedido> itens;
    private Preco valorTotal;
    private String observacoes;
    private String usuarioId; // Para futuro login
    private LocalDateTime dataPedido;
    
    private Pedido() {
        super();
        this.itens = new ArrayList<>();
        this.status = StatusPedido.PENDENTE;
        this.dataPedido = LocalDateTime.now();
    }
    
    public static Pedido criar(NumeroPedido numeroPedido, String clienteId, String clienteNome, String usuarioId) {
        validarDados(numeroPedido, clienteId, clienteNome);
        
        Pedido pedido = new Pedido();
        pedido.numeroPedido = numeroPedido;
        pedido.clienteId = clienteId;
        pedido.clienteNome = clienteNome;
        pedido.usuarioId = usuarioId;
        pedido.valorTotal = Preco.zero();
        pedido.touch();
        return pedido;
    }
    
    public void adicionarItem(ItemPedido item) {
        if (item == null) {
            throw new ValidationException("Item não pode ser nulo");
        }
        if (status == StatusPedido.FINALIZADO || status == StatusPedido.CANCELADO) {
            throw new ValidationException("Não é possível adicionar itens a um pedido finalizado ou cancelado");
        }
        
        itens.add(item);
        recalcularValorTotal();
        touch();
    }
    
    public void removerItem(int indice) {
        if (indice < 0 || indice >= itens.size()) {
            throw new ValidationException("Índice do item inválido");
        }
        if (status == StatusPedido.FINALIZADO || status == StatusPedido.CANCELADO) {
            throw new ValidationException("Não é possível remover itens de um pedido finalizado ou cancelado");
        }
        
        itens.remove(indice);
        recalcularValorTotal();
        touch();
    }
    
    public void atualizarStatus(StatusPedido novoStatus) {
        if (novoStatus == null) {
            throw new ValidationException("Status não pode ser nulo");
        }
        if (!status.podeSerAtualizadoPara(novoStatus)) {
            throw new ValidationException("Não é possível atualizar o status de " + status.getDescricao() + " para " + novoStatus.getDescricao());
        }
        
        this.status = novoStatus;
        touch();
    }
    
    public void atualizarObservacoes(String novasObservacoes) {
        this.observacoes = novasObservacoes != null ? novasObservacoes.trim() : null;
        touch();
    }
    
    public void cancelar() {
        if (status == StatusPedido.FINALIZADO) {
            throw new ValidationException("Não é possível cancelar um pedido finalizado");
        }
        this.status = StatusPedido.CANCELADO;
        touch();
    }
    
    public boolean estaFinalizado() {
        return status == StatusPedido.FINALIZADO;
    }
    
    public boolean estaCancelado() {
        return status == StatusPedido.CANCELADO;
    }
    
    private void recalcularValorTotal() {
        Preco total = Preco.zero();
        for (ItemPedido item : itens) {
            total = total.add(item.calcularSubtotal());
        }
        this.valorTotal = total;
    }
    
    /**
     * Restaura o ID e timestamps do banco de dados (usado pelos mappers).
     */
    public void restaurarDoBanco(String id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        restaurarId(id);
        restaurarTimestamps(createdAt, updatedAt);
    }
    
    private static void validarDados(NumeroPedido numeroPedido, String clienteId, String clienteNome) {
        if (numeroPedido == null) {
            throw new ValidationException("Número do pedido não pode ser nulo");
        }
        if (clienteId == null || clienteId.trim().isEmpty()) {
            throw new ValidationException("ID do cliente não pode ser nulo ou vazio");
        }
        if (clienteNome == null || clienteNome.trim().isEmpty()) {
            throw new ValidationException("Nome do cliente não pode ser nulo ou vazio");
        }
    }
}

