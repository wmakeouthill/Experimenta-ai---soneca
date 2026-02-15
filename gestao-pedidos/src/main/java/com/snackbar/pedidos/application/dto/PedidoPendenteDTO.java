package com.snackbar.pedidos.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO para pedido pendente de aceitação (na fila).
 * Pode ser de origem MESA (QR code) ou TOTEM (auto atendimento).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoPendenteDTO {

    public static final String TIPO_MESA = "MESA";
    public static final String TIPO_TOTEM = "TOTEM";

    private String id;
    /** Origem: MESA ou TOTEM */
    private String tipo;
    private String mesaToken;
    private String mesaId;
    private Integer numeroMesa;
    private String clienteId;
    private String nomeCliente;
    private String telefoneCliente;
    private List<ItemPedidoPendenteDTO> itens;
    private List<MeioPagamentoRequest> meiosPagamento;
    private String observacoes;
    private BigDecimal valorTotal;
    private LocalDateTime dataHoraSolicitacao;
    private long tempoEsperaSegundos;

    /**
     * Calcula o tempo de espera em segundos.
     */
    public void atualizarTempoEspera() {
        if (dataHoraSolicitacao != null) {
            this.tempoEsperaSegundos = java.time.Duration.between(
                    dataHoraSolicitacao,
                    LocalDateTime.now()).getSeconds();
        }
    }
}
