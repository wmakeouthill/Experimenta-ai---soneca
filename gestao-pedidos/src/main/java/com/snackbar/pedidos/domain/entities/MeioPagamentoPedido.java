package com.snackbar.pedidos.domain.entities;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;

import lombok.Getter;

@Getter
public class MeioPagamentoPedido {
    private MeioPagamento meioPagamento;
    private Preco valor;
    private Preco valorPagoDinheiro;
    private Preco troco;

    private MeioPagamentoPedido() {
    }

    /**
     * Cria um meio de pagamento sem troco (padrão para todos exceto dinheiro).
     */
    public static MeioPagamentoPedido criar(MeioPagamento meioPagamento, Preco valor) {
        validarDados(meioPagamento, valor);

        MeioPagamentoPedido meioPagamentoPedido = new MeioPagamentoPedido();
        meioPagamentoPedido.meioPagamento = meioPagamento;
        meioPagamentoPedido.valor = valor;
        return meioPagamentoPedido;
    }

    /**
     * Cria um meio de pagamento em dinheiro com cálculo automático de troco.
     * O valorPago é o valor que o cliente entregou em nota.
     * O troco é calculado automaticamente: valorPago - valor do pedido.
     */
    public static MeioPagamentoPedido criarComTroco(Preco valor, Preco valorPagoDinheiro) {
        validarDados(MeioPagamento.DINHEIRO, valor);

        if (valorPagoDinheiro == null) {
            throw new ValidationException("Valor pago em dinheiro não pode ser nulo");
        }
        if (valorPagoDinheiro.isLessThan(valor)) {
            throw new ValidationException("Valor pago em dinheiro não pode ser menor que o valor do pedido");
        }

        MeioPagamentoPedido meioPagamentoPedido = new MeioPagamentoPedido();
        meioPagamentoPedido.meioPagamento = MeioPagamento.DINHEIRO;
        meioPagamentoPedido.valor = valor;
        meioPagamentoPedido.valorPagoDinheiro = valorPagoDinheiro;
        meioPagamentoPedido.troco = valorPagoDinheiro.subtract(valor);
        return meioPagamentoPedido;
    }

    /**
     * Restaura um meio de pagamento do banco com dados de troco.
     */
    public static MeioPagamentoPedido restaurarComTroco(
            MeioPagamento meioPagamento, Preco valor, Preco valorPagoDinheiro, Preco troco) {
        MeioPagamentoPedido meioPagamentoPedido = new MeioPagamentoPedido();
        meioPagamentoPedido.meioPagamento = meioPagamento;
        meioPagamentoPedido.valor = valor;
        meioPagamentoPedido.valorPagoDinheiro = valorPagoDinheiro;
        meioPagamentoPedido.troco = troco;
        return meioPagamentoPedido;
    }

    /**
     * Verifica se este pagamento possui troco calculado.
     */
    public boolean possuiTroco() {
        return troco != null && troco.isGreaterThan(Preco.zero());
    }

    /**
     * Corrige o valor pago em dinheiro e recalcula o troco.
     * Usado quando o operador precisa informar/corrigir o valor real
     * que o cliente pagou em dinheiro (ex: cliente esqueceu de informar no CTA).
     *
     * @param novoValorPago o valor real entregue pelo cliente em nota
     */
    public void corrigirTroco(Preco novoValorPago) {
        if (this.meioPagamento != MeioPagamento.DINHEIRO) {
            throw new ValidationException("Só é possível corrigir troco de pagamento em dinheiro");
        }
        if (novoValorPago == null) {
            throw new ValidationException("Valor pago em dinheiro não pode ser nulo");
        }
        if (novoValorPago.isLessThan(this.valor)) {
            throw new ValidationException("Valor pago em dinheiro não pode ser menor que o valor do pedido");
        }

        this.valorPagoDinheiro = novoValorPago;
        this.troco = novoValorPago.subtract(this.valor);
    }

    private static void validarDados(MeioPagamento meioPagamento, Preco valor) {
        if (meioPagamento == null) {
            throw new ValidationException("Meio de pagamento não pode ser nulo");
        }
        if (valor == null) {
            throw new ValidationException("Valor não pode ser nulo");
        }
        if (!valor.isGreaterThan(Preco.zero())) {
            throw new ValidationException("Valor deve ser maior que zero");
        }
    }
}
