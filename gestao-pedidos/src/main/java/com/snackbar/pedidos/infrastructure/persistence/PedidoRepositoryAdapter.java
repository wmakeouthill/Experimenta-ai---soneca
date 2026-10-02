package com.snackbar.pedidos.infrastructure.persistence;

import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.StatusPedido;
import com.snackbar.pedidos.domain.services.PosicaoNaSessao;
import com.snackbar.pedidos.infrastructure.mappers.PedidoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PedidoRepositoryAdapter implements PedidoRepositoryPort {

    private final PedidoJpaRepository jpaRepository;
    private final PedidoMapper mapper;

    @Override
    @SuppressWarnings("null") // jpaRepository.save() nunca retorna null
    public Pedido salvar(@NonNull Pedido pedido) {
        PedidoEntity entity = mapper.paraEntity(pedido);
        PedidoEntity salvo = jpaRepository.saveAndFlush(entity);
        return mapear(salvo);
    }

    @Override
    public Optional<Pedido> buscarPorId(@NonNull String id) {
        return jpaRepository.findById(id)
                .map(this::mapear);
    }

    @Override
    public List<Pedido> buscarTodos() {
        return mapear(jpaRepository.findAll());
    }

    @Override
    public List<Pedido> buscarPorStatus(StatusPedido status) {
        return mapear(jpaRepository.findByStatus(status));
    }

    @Override
    public List<Pedido> buscarPorClienteId(String clienteId) {
        return mapear(jpaRepository.findByClienteId(clienteId));
    }

    @Override
    public Page<Pedido> buscarPorClienteId(String clienteId, Pageable pageable) {
        Page<PedidoEntity> page = jpaRepository.findByClienteId(clienteId, pageable);
        List<Pedido> pedidos = mapear(page.getContent());
        return new PageImpl<>(pedidos, pageable, page.getTotalElements());
    }

    @Override
    public List<Pedido> buscarPorDataPedido(LocalDateTime dataInicio, LocalDateTime dataFim) {
        return mapear(jpaRepository.findByDataPedidoBetween(dataInicio, dataFim));
    }

    @Override
    public List<Pedido> buscarPorStatusEData(StatusPedido status, LocalDateTime dataInicio, LocalDateTime dataFim) {
        return mapear(jpaRepository.findByStatusAndDataPedidoBetween(status, dataInicio, dataFim));
    }

    @Override
    public List<Pedido> buscarPorSessaoId(String sessaoId) {
        return mapear(jpaRepository.findBySessaoId(sessaoId));
    }

    @Override
    public List<Pedido> buscarPorDataInicioSessao(LocalDate dataInicio) {
        return mapear(jpaRepository.findByDataInicioSessao(java.sql.Date.valueOf(dataInicio)));
    }

    @Override
    public int buscarUltimoNumeroPedido() {
        return jpaRepository.findMaxNumeroPedido()
                .orElse(0);
    }

    @Override
    public void excluir(@NonNull String id) {
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pedido> buscarAbertosPorMesaESemPagamento(String mesaId, String clienteId) {
        List<StatusPedido> statusAbertos = List.of(
                StatusPedido.PENDENTE, StatusPedido.PREPARANDO, StatusPedido.PRONTO);
        return mapear(jpaRepository.findAbertosSemPagamento(mesaId, clienteId, statusAbertos));
    }

    private Pedido mapear(PedidoEntity entity) {
        Pedido pedido = mapper.paraDomain(entity);
        preencherNumeroExibicao(List.of(pedido));
        return pedido;
    }

    private List<Pedido> mapear(List<PedidoEntity> entities) {
        List<Pedido> pedidos = entities.stream().map(mapper::paraDomain).toList();
        preencherNumeroExibicao(pedidos);
        return pedidos;
    }

    private void preencherNumeroExibicao(List<Pedido> pedidos) {
        Set<String> sessoes = new HashSet<>();
        for (Pedido pedido : pedidos) {
            if (pedido.getSessaoId() != null && !pedido.getSessaoId().isBlank()) {
                sessoes.add(pedido.getSessaoId());
            }
        }
        if (sessoes.isEmpty()) {
            return;
        }

        List<PosicaoNaSessao.Marcador> marcadores = jpaRepository.findBySessaoIdIn(sessoes).stream()
                .map(referencia -> new PosicaoNaSessao.Marcador(
                        referencia.getId(),
                        referencia.getSessaoId(),
                        referencia.getDataPedido(),
                        referencia.getNumeroPedido()))
                .toList();
        Map<String, String> posicoes = PosicaoNaSessao.calcular(marcadores);
        for (Pedido pedido : pedidos) {
            String exibicao = posicoes.get(pedido.getId());
            if (exibicao != null) {
                pedido.definirNumeroExibicao(exibicao);
            }
        }
    }
}
