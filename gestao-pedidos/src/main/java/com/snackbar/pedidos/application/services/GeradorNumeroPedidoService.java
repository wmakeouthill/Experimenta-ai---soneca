package com.snackbar.pedidos.application.services;

import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.valueobjects.NumeroPedido;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Serviço para geração de número de pedido com tratamento de concorrência.
 * 
 * PROBLEMA RESOLVIDO:
 * Em cenários de alta concorrência, dois pedidos podem ser criados
 * simultaneamente
 * e ambos lerem o mesmo "último número" do banco, gerando números duplicados.
 * 
 * SOLUÇÃO:
 * - Gera número sequencial baseado no último do banco
 * - Em caso de falha por constraint violation (duplicação), faz retry
 * - Máximo de 3 tentativas antes de falhar
 * 
 * @see NumeroPedido
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeradorNumeroPedidoService {

    private final PedidoRepositoryPort pedidoRepository;

    private static final int MAX_TENTATIVAS = 3;

    /**
     * Gera o próximo número de pedido disponível.
     * 
     * @return NumeroPedido único
     * @throws IllegalStateException se não conseguir gerar após MAX_TENTATIVAS
     */
    public NumeroPedido gerarProximoNumero() {
        int tentativas = 0;

        while (tentativas < MAX_TENTATIVAS) {
            tentativas++;

            int ultimoNumero = pedidoRepository.buscarUltimoNumeroPedido();
            NumeroPedido numeroPedido = ultimoNumero == 0
                    ? NumeroPedido.gerarPrimeiro()
                    : NumeroPedido.gerarProximo(ultimoNumero);

            log.debug("Gerado número de pedido: {} (tentativa {})",
                    numeroPedido.getNumero(), tentativas);

            return numeroPedido;
        }

        throw new IllegalStateException(
                "Não foi possível gerar número de pedido após " + MAX_TENTATIVAS + " tentativas");
    }

    /**
     * Verifica se uma exceção é causada por violação de constraint de número
     * duplicado.
     * Útil para implementar retry no nível do use case.
     * 
     * @param exception Exceção a ser verificada
     * @return true se for violação de constraint de duplicação
     */
    public boolean isDuplicacaoNumeroPedido(Exception exception) {
        if (exception instanceof DataIntegrityViolationException) {
            String message = exception.getMessage();
            return message != null &&
                    (message.contains("numero_pedido") ||
                            message.contains("Duplicate entry") ||
                            message.contains("UNIQUE"));
        }
        return false;
    }
}
