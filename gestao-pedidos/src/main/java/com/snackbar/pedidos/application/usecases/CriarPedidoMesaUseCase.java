package com.snackbar.pedidos.application.usecases;

import com.snackbar.cardapio.domain.valueobjects.Preco;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.CriarPedidoMesaRequest;
import com.snackbar.pedidos.application.dto.ItemPedidoRequest;
import com.snackbar.pedidos.application.dto.PedidoDTO;
import com.snackbar.pedidos.application.ports.CardapioServicePort;
import com.snackbar.pedidos.application.ports.MesaRepositoryPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import com.snackbar.pedidos.domain.entities.ItemPedido;
import com.snackbar.pedidos.domain.entities.Mesa;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.exceptions.MesaNaoEncontradaException;
import com.snackbar.pedidos.domain.services.PedidoValidator;
import com.snackbar.pedidos.domain.valueobjects.NumeroPedido;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case para criar pedido via mesa (cliente escaneando QR code).
 * Este pedido não tem meio de pagamento definido, pois será pago no caixa.
 */
@Service
@RequiredArgsConstructor
public class CriarPedidoMesaUseCase {

    private final PedidoRepositoryPort pedidoRepository;
    private final MesaRepositoryPort mesaRepository;
    private final CardapioServicePort cardapioService;
    private final PedidoValidator pedidoValidator;
    private final SessaoTrabalhoRepositoryPort sessaoTrabalhoRepository;

    private static final String USUARIO_SISTEMA = "SISTEMA"; // ID para pedidos via QR code

    @Transactional
    public PedidoDTO executar(@NonNull String qrCodeToken, CriarPedidoMesaRequest request) {
        Mesa mesa = validarEBuscarMesa(qrCodeToken);

        int ultimoNumero = pedidoRepository.buscarUltimoNumeroPedido();
        NumeroPedido numeroPedido = ultimoNumero == 0
                ? NumeroPedido.gerarPrimeiro()
                : NumeroPedido.gerarProximo(ultimoNumero);

        // Usa o clienteId do cliente identificado pelo telefone
        Pedido pedido = Pedido.criar(
                numeroPedido,
                request.getClienteId(), // clienteId do cliente identificado
                request.getNomeCliente(), // nome do cliente
                USUARIO_SISTEMA);

        // Define a mesa e o nome do cliente
        pedido.definirMesa(mesa.getId(), request.getNomeCliente());

        // Adiciona os itens
        for (ItemPedidoRequest itemRequest : request.getItens()) {
            validarProdutoDisponivel(itemRequest.getProdutoId());

            var produtoDTO = cardapioService.buscarProdutoPorId(itemRequest.getProdutoId());
            Preco precoUnitario = Preco.of(produtoDTO.getPreco());

            ItemPedido item = ItemPedido.criar(
                    itemRequest.getProdutoId(),
                    produtoDTO.getNome(),
                    itemRequest.getQuantidade(),
                    precoUnitario,
                    itemRequest.getObservacoes());

            pedido.adicionarItem(item);
        }

        pedido.atualizarObservacoes(request.getObservacoes());

        pedidoValidator.validarCriacao(pedido);

        vincularSessaoAtiva(pedido);

        Pedido pedidoSalvo = pedidoRepository.salvar(pedido);

        return PedidoDTO.de(pedidoSalvo);
    }

    private Mesa validarEBuscarMesa(String qrCodeToken) {
        Mesa mesa = mesaRepository.buscarPorQrCodeToken(qrCodeToken)
                .orElseThrow(() -> MesaNaoEncontradaException.porToken(qrCodeToken));

        if (!mesa.isAtiva()) {
            throw new ValidationException("Esta mesa não está ativa para receber pedidos");
        }

        return mesa;
    }

    private void vincularSessaoAtiva(Pedido pedido) {
        sessaoTrabalhoRepository.buscarSessaoAtiva()
                .ifPresent(sessao -> pedido.definirSessaoId(sessao.getId()));
    }

    private void validarProdutoDisponivel(String produtoId) {
        if (!cardapioService.produtoEstaDisponivel(produtoId)) {
            throw new ValidationException("Produto não está disponível: " + produtoId);
        }
    }
}
