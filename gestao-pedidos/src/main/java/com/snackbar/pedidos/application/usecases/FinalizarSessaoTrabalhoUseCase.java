package com.snackbar.pedidos.application.usecases;

import com.snackbar.kernel.domain.exceptions.BusinessRuleException;
import com.snackbar.pedidos.application.dto.SessaoTrabalhoDTO;
import com.snackbar.pedidos.application.ports.ObterNomeUsuarioPort;
import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import com.snackbar.pedidos.application.services.FilaPedidosMesaService;
import com.snackbar.pedidos.application.services.FilaPedidosTotemService;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.SessaoTrabalho;
import com.snackbar.pedidos.domain.entities.StatusPedido;
import com.snackbar.kernel.domain.exceptions.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FinalizarSessaoTrabalhoUseCase {

    private final SessaoTrabalhoRepositoryPort repository;
    private final PedidoRepositoryPort pedidoRepository;
    private final ObterNomeUsuarioPort obterNomeUsuarioPort;
    private final FilaPedidosMesaService filaPedidosMesa;
    private final FilaPedidosTotemService filaPedidosTotem;

    @SuppressWarnings("null") // repository.salvar() nunca retorna null, .get() nunca retorna null porque validamos antes
    public SessaoTrabalhoDTO executar(@NonNull String sessaoId, @NonNull BigDecimal valorFechamento) {
        SessaoTrabalho sessao = buscarSessao(sessaoId);
        validarPedidosPendentes(sessaoId);
        sessao.finalizar(valorFechamento);
        SessaoTrabalho sessaoSalva = repository.salvar(sessao);
        SessaoTrabalhoDTO dto = SessaoTrabalhoDTO.de(sessaoSalva);
        String nome = obterNomeUsuarioPort.obterNomesPorIds(Collections.singleton(sessaoSalva.getUsuarioId()))
            .getOrDefault(sessaoSalva.getUsuarioId(), sessaoSalva.getUsuarioId());
        dto.setUsuarioNome(nome);
        return dto;
    }
    
    private void validarPedidosPendentes(@NonNull String sessaoId) {
        List<Pedido> pedidos = pedidoRepository.buscarPorSessaoId(sessaoId);
        long aguardando = pedidos.stream().filter(p -> p.getStatus() == StatusPedido.PENDENTE).count();
        long preparando = pedidos.stream().filter(p -> p.getStatus() == StatusPedido.PREPARANDO).count();
        // Fila de mesa/totem ainda não aceita: aceita depois do fechamento viraria pedido sem sessão
        long naFila = filaPedidosMesa.quantidadePedidosPendentes() + filaPedidosTotem.quantidadePedidosPendentes();

        List<String> pendencias = new ArrayList<>();
        if (aguardando > 0) {
            pendencias.add(aguardando + " aguardando");
        }
        if (preparando > 0) {
            pendencias.add(preparando + " em preparação");
        }
        if (naFila > 0) {
            pendencias.add(naFila + " na fila de aceite da mesa/totem");
        }
        if (!pendencias.isEmpty()) {
            throw new BusinessRuleException("Não é possível finalizar a sessão. Existem pedidos pendentes: "
                    + String.join(", ", pendencias) + ".");
        }
    }

    private SessaoTrabalho buscarSessao(@NonNull String sessaoId) {
        Optional<SessaoTrabalho> sessao = repository.buscarPorId(sessaoId);
        if (sessao.isEmpty()) {
            throw new RecursoNaoEncontradoException("Sessão não encontrada: " + sessaoId);
        }
        return sessao.get();
    }
}

