package com.snackbar.pedidos.application.usecases;

import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;
import com.snackbar.pedidos.application.dto.ContaMesaDTO;
import com.snackbar.pedidos.application.dto.ContaMesaDTO.ItemContaDTO;
import com.snackbar.pedidos.application.ports.ConfiguracaoPagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.MesaRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.Mesa;
import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.kernel.domain.exceptions.RecursoNaoEncontradoException;

import lombok.RequiredArgsConstructor;

/**
 * Retorna a previa da conta (pedidos em aberto e total) sem gerar pagamento.
 */
@Service
@RequiredArgsConstructor
public class ConsultarContaMesaUseCase {

    private final PedidoRepositoryPort pedidoRepository;
    private final MesaRepositoryPort mesaRepository;
    private final ConfiguracaoPagamentoRepositoryPort configuracaoRepository;

    @Transactional(readOnly = true)
    public ContaMesaDTO executar(String mesaToken, String clienteId) {
        if (clienteId == null || clienteId.isBlank()) {
            throw new ValidationException("Identificacao do cliente e obrigatoria");
        }

        ConfiguracaoPagamentoDTO config = configuracaoRepository.buscar();
        if (!config.pixMesaAtivo()) {
            throw new ValidationException("PIX na mesa nao esta habilitado");
        }
        if (config.modoMesa() != ModoPagamentoMesa.POS_PAGO) {
            throw new ValidationException("Conta pos-paga so esta disponivel no modo POS_PAGO");
        }

        Mesa mesa = mesaRepository.buscarPorQrCodeToken(mesaToken)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mesa nao encontrada"));

        List<Pedido> pedidos = pedidoRepository.buscarAbertosPorMesaESemPagamento(mesa.getId(), clienteId);

        List<ItemContaDTO> itens = pedidos.stream()
                .map(p -> new ItemContaDTO(
                        p.getId(),
                        p.getNumeroExibicao() != null ? p.getNumeroExibicao() : p.getNumeroPedido().getNumero(),
                        p.getValorTotal().getAmount()
                                .movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()))
                .toList();

        long total = itens.stream().mapToLong(ItemContaDTO::valorCentavos).sum();

        return new ContaMesaDTO(null, mesa.getNumero(), "ABERTA", total, itens);
    }
}
