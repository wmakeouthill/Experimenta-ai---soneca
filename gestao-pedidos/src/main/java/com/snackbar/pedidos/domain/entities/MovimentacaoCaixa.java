package com.snackbar.pedidos.domain.entities;

import com.snackbar.kernel.domain.entities.BaseEntity;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade de domínio que representa uma movimentação de caixa.
 * Registra todas as transações em dinheiro durante uma sessão de trabalho.
 */
@Getter
public class MovimentacaoCaixa extends BaseEntity {
    private String sessaoId;
    private String pedidoId;
    private String usuarioId;
    private TipoMovimentacaoCaixa tipo;
    private BigDecimal valor;
    private String descricao;
    private LocalDateTime dataMovimentacao;

    private MovimentacaoCaixa() {
        super();
        this.dataMovimentacao = LocalDateTime.now();
    }

    /**
     * Cria uma movimentação de abertura de caixa.
     */
    public static MovimentacaoCaixa criarAbertura(String sessaoId, BigDecimal valor) {
        validarSessaoId(sessaoId);
        validarValor(valor);

        MovimentacaoCaixa movimentacao = new MovimentacaoCaixa();
        movimentacao.sessaoId = sessaoId;
        movimentacao.tipo = TipoMovimentacaoCaixa.ABERTURA;
        movimentacao.valor = valor;
        movimentacao.descricao = "Abertura de caixa";
        movimentacao.touch();
        return movimentacao;
    }

    /**
     * Cria uma movimentação de venda em dinheiro.
     */
    public static MovimentacaoCaixa criarVendaDinheiro(String sessaoId, String pedidoId, BigDecimal valor) {
        validarSessaoId(sessaoId);
        validarPedidoId(pedidoId);
        validarValorPositivo(valor);

        MovimentacaoCaixa movimentacao = new MovimentacaoCaixa();
        movimentacao.sessaoId = sessaoId;
        movimentacao.pedidoId = pedidoId;
        movimentacao.tipo = TipoMovimentacaoCaixa.VENDA_DINHEIRO;
        movimentacao.valor = valor;
        movimentacao.descricao = "Venda em dinheiro";
        movimentacao.touch();
        return movimentacao;
    }

    /**
     * Cria uma movimentação de fechamento de caixa.
     */
    public static MovimentacaoCaixa criarFechamento(String sessaoId, BigDecimal valor) {
        validarSessaoId(sessaoId);
        validarValor(valor);

        MovimentacaoCaixa movimentacao = new MovimentacaoCaixa();
        movimentacao.sessaoId = sessaoId;
        movimentacao.tipo = TipoMovimentacaoCaixa.FECHAMENTO;
        movimentacao.valor = valor;
        movimentacao.descricao = "Fechamento de caixa";
        movimentacao.touch();
        return movimentacao;
    }

    /**
     * Cria uma movimentação de sangria (retirada de dinheiro).
     */
    public static MovimentacaoCaixa criarSangria(String sessaoId, String usuarioId, BigDecimal valor, String descricao) {
        validarSessaoId(sessaoId);
        validarValorPositivo(valor);

        MovimentacaoCaixa movimentacao = new MovimentacaoCaixa();
        movimentacao.sessaoId = sessaoId;
        movimentacao.usuarioId = usuarioId;
        movimentacao.tipo = TipoMovimentacaoCaixa.SANGRIA;
        movimentacao.valor = valor.negate();
        movimentacao.descricao = descricao != null ? descricao : "Sangria de caixa";
        movimentacao.touch();
        return movimentacao;
    }

    /**
     * Cria uma movimentação de suprimento (entrada de dinheiro).
     */
    public static MovimentacaoCaixa criarSuprimento(String sessaoId, String usuarioId, BigDecimal valor, String descricao) {
        validarSessaoId(sessaoId);
        validarValorPositivo(valor);

        MovimentacaoCaixa movimentacao = new MovimentacaoCaixa();
        movimentacao.sessaoId = sessaoId;
        movimentacao.usuarioId = usuarioId;
        movimentacao.tipo = TipoMovimentacaoCaixa.SUPRIMENTO;
        movimentacao.valor = valor;
        movimentacao.descricao = descricao != null ? descricao : "Suprimento de caixa";
        movimentacao.touch();
        return movimentacao;
    }

    /**
     * Factory method para restaurar uma movimentação do banco de dados.
     */
    public static MovimentacaoCaixa restaurarDoBancoFactory(
            String id,
            String sessaoId,
            String pedidoId,
            String usuarioId,
            TipoMovimentacaoCaixa tipo,
            BigDecimal valor,
            String descricao,
            LocalDateTime dataMovimentacao,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        MovimentacaoCaixa movimentacao = new MovimentacaoCaixa();
        movimentacao.sessaoId = sessaoId;
        movimentacao.pedidoId = pedidoId;
        movimentacao.usuarioId = usuarioId;
        movimentacao.tipo = tipo;
        movimentacao.valor = valor;
        movimentacao.descricao = descricao;
        movimentacao.dataMovimentacao = dataMovimentacao;
        movimentacao.restaurarId(id);
        movimentacao.restaurarTimestamps(createdAt, updatedAt);
        return movimentacao;
    }

    private static void validarSessaoId(String sessaoId) {
        if (sessaoId == null || sessaoId.trim().isEmpty()) {
            throw new ValidationException("ID da sessão não pode ser nulo ou vazio");
        }
    }

    private static void validarPedidoId(String pedidoId) {
        if (pedidoId == null || pedidoId.trim().isEmpty()) {
            throw new ValidationException("ID do pedido não pode ser nulo ou vazio");
        }
    }

    private static void validarValor(BigDecimal valor) {
        if (valor == null) {
            throw new ValidationException("Valor não pode ser nulo");
        }
        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Valor não pode ser negativo");
        }
    }

    private static void validarValorPositivo(BigDecimal valor) {
        if (valor == null) {
            throw new ValidationException("Valor não pode ser nulo");
        }
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Valor deve ser maior que zero");
        }
    }
}

