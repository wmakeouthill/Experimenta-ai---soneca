package com.snackbar.pedidos.domain.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.snackbar.kernel.domain.exceptions.ValidationException;

/**
 * Conta pos-paga de mesa: consolida os pedidos abertos de um cliente numa mesa
 * para pagamento unico via PIX. Dominio puro (sem Spring).
 */
public class ContaMesa {

    private final String id;
    private final String mesaId;
    private final int numeroMesa;
    private final String clienteId;
    private StatusContaMesa status;
    private final long valorCentavos;
    private final String correlationId;
    private final List<String> pedidoIds;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private ContaMesa(String id, String mesaId, int numeroMesa, String clienteId,
                      StatusContaMesa status, long valorCentavos, String correlationId,
                      List<String> pedidoIds, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.mesaId = mesaId;
        this.numeroMesa = numeroMesa;
        this.clienteId = clienteId;
        this.status = status;
        this.valorCentavos = valorCentavos;
        this.correlationId = correlationId;
        this.pedidoIds = new ArrayList<>(pedidoIds);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ContaMesa abrir(String mesaId, int numeroMesa, String clienteId,
                                  List<String> pedidoIds, long valorCentavos, String correlationId) {
        if (mesaId == null || mesaId.isBlank()) {
            throw new ValidationException("mesaId e obrigatorio");
        }
        if (clienteId == null || clienteId.isBlank()) {
            throw new ValidationException("clienteId e obrigatorio");
        }
        if (pedidoIds == null || pedidoIds.isEmpty()) {
            throw new ValidationException("A conta precisa de ao menos um pedido");
        }
        if (valorCentavos <= 0) {
            throw new ValidationException("Valor da conta deve ser positivo");
        }
        LocalDateTime agora = LocalDateTime.now();
        return new ContaMesa(UUID.randomUUID().toString(), mesaId, numeroMesa, clienteId,
                StatusContaMesa.ABERTA, valorCentavos, correlationId, pedidoIds, agora, agora);
    }

    /**
     * Reconstitui a partir da persistencia.
     */
    public static ContaMesa restaurar(String id, String mesaId, int numeroMesa, String clienteId,
                                      StatusContaMesa status, long valorCentavos, String correlationId,
                                      List<String> pedidoIds, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new ContaMesa(id, mesaId, numeroMesa, clienteId, status, valorCentavos,
                correlationId, pedidoIds, createdAt, updatedAt);
    }

    public void pagar() {
        if (status == StatusContaMesa.PAGA) {
            return; // idempotente
        }
        if (status == StatusContaMesa.CANCELADA) {
            throw new ValidationException("Conta cancelada nao pode ser paga");
        }
        this.status = StatusContaMesa.PAGA;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancelar() {
        if (status == StatusContaMesa.PAGA) {
            throw new ValidationException("Conta paga nao pode ser cancelada");
        }
        this.status = StatusContaMesa.CANCELADA;
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getMesaId() { return mesaId; }
    public int getNumeroMesa() { return numeroMesa; }
    public String getClienteId() { return clienteId; }
    public StatusContaMesa getStatus() { return status; }
    public long getValorCentavos() { return valorCentavos; }
    public String getCorrelationId() { return correlationId; }
    public List<String> getPedidoIds() { return Collections.unmodifiableList(pedidoIds); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
