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
    private List<MeioPagamentoPedido> meiosPagamento;
    private String usuarioId; // Para futuro login
    private String sessaoId; // ID da sessão de trabalho
    private LocalDateTime dataPedido;
    private LocalDateTime dataFinalizacao; // Data definitiva de finalização (imutável após definida)
    
    private Pedido() {
        super();
        this.itens = new ArrayList<>();
        this.meiosPagamento = new ArrayList<>();
        this.status = StatusPedido.PENDENTE;
        this.dataPedido = LocalDateTime.now();
        this.dataFinalizacao = null; // Inicialmente nulo, será definido apenas quando finalizado
    }
    
    public static Pedido criar(NumeroPedido numeroPedido, String clienteId, String clienteNome, String usuarioId) {
        validarDados(numeroPedido, clienteId, clienteNome, usuarioId);
        
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
        
        // Define data de finalização apenas quando o status muda para FINALIZADO pela primeira vez
        if (novoStatus == StatusPedido.FINALIZADO && this.dataFinalizacao == null) {
            this.dataFinalizacao = LocalDateTime.now();
        }
        
        this.status = novoStatus;
        touch();
    }
    
    public void atualizarObservacoes(String novasObservacoes) {
        this.observacoes = novasObservacoes != null ? novasObservacoes.trim() : null;
        touch();
    }
    
    public void adicionarMeioPagamento(MeioPagamentoPedido meioPagamentoPedido) {
        if (meioPagamentoPedido == null) {
            throw new ValidationException("Meio de pagamento não pode ser nulo");
        }
        if (status == StatusPedido.FINALIZADO || status == StatusPedido.CANCELADO) {
            throw new ValidationException("Não é possível adicionar meios de pagamento a um pedido finalizado ou cancelado");
        }
        this.meiosPagamento.add(meioPagamentoPedido);
        touch();
    }
    
    public void removerMeioPagamento(int indice) {
        if (indice < 0 || indice >= meiosPagamento.size()) {
            throw new ValidationException("Índice do meio de pagamento inválido");
        }
        if (status == StatusPedido.FINALIZADO || status == StatusPedido.CANCELADO) {
            throw new ValidationException("Não é possível remover meios de pagamento de um pedido finalizado ou cancelado");
        }
        meiosPagamento.remove(indice);
        touch();
    }
    
    public Preco calcularTotalMeiosPagamento() {
        Preco total = Preco.zero();
        for (MeioPagamentoPedido meioPagamento : meiosPagamento) {
            total = total.add(meioPagamento.getValor());
        }
        return total;
    }
    
    public void cancelar() {
        // Permite cancelar pedidos finalizados para casos especiais
        this.status = StatusPedido.CANCELADO;
        touch();
    }
    
    public void definirSessaoId(String sessaoId) {
        this.sessaoId = sessaoId;
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
    
    /**
     * Restaura itens do banco de dados sem validações (usado pelos mappers).
     * Este método é usado quando estamos apenas restaurando dados existentes,
     * não adicionando novos itens, então não precisa validar status.
     */
    public void restaurarItensDoBanco(List<ItemPedido> itensRestaurados) {
        if (itensRestaurados == null) {
            this.itens = new ArrayList<>();
        } else {
            this.itens = new ArrayList<>(itensRestaurados);
        }
        recalcularValorTotal();
    }
    
    /**
     * Restaura meios de pagamento do banco de dados sem validações (usado pelos mappers).
     */
    public void restaurarMeiosPagamentoDoBanco(List<MeioPagamentoPedido> meiosPagamentoRestaurados) {
        if (meiosPagamentoRestaurados == null) {
            this.meiosPagamento = new ArrayList<>();
        } else {
            this.meiosPagamento = new ArrayList<>(meiosPagamentoRestaurados);
        }
    }
    
    /**
     * Restaura a data do pedido do banco de dados (usado pelos mappers).
     * Este método preserva a data original de criação do pedido.
     */
    public void restaurarDataPedidoDoBanco(LocalDateTime dataPedido) {
        if (dataPedido != null) {
            this.dataPedido = dataPedido;
        }
    }
    
    /**
     * Restaura a data de finalização do banco de dados (usado pelos mappers).
     * Este método preserva a data original de finalização do pedido.
     * IMPORTANTE: A data de finalização é imutável após ser definida.
     * Este método é usado apenas ao restaurar do banco, então sempre restaura o valor do banco.
     */
    public void restaurarDataFinalizacaoDoBanco(LocalDateTime dataFinalizacao) {
        // Sempre restaura do banco (este método é usado apenas na restauração)
        this.dataFinalizacao = dataFinalizacao;
    }
    
    private static void validarDados(NumeroPedido numeroPedido, String clienteId, String clienteNome, String usuarioId) {
        if (numeroPedido == null) {
            throw new ValidationException("Número do pedido não pode ser nulo");
        }
        if (clienteId == null || clienteId.trim().isEmpty()) {
            throw new ValidationException("ID do cliente não pode ser nulo ou vazio");
        }
        if (clienteNome == null || clienteNome.trim().isEmpty()) {
            throw new ValidationException("Nome do cliente não pode ser nulo ou vazio");
        }
        if (usuarioId == null || usuarioId.trim().isEmpty()) {
            throw new ValidationException("ID do usuário é obrigatório");
        }
    }
}

