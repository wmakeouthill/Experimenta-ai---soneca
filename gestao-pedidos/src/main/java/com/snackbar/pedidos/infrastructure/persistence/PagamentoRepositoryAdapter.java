package com.snackbar.pedidos.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.domain.entities.Pagamento;
import com.snackbar.pedidos.domain.entities.StatusPagamento;
import com.snackbar.pedidos.infrastructure.mappers.PagamentoMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PagamentoRepositoryAdapter implements PagamentoRepositoryPort {

    private static final List<StatusPagamento> STATUS_PENDENTES = List.of(
            StatusPagamento.INICIADO,
            StatusPagamento.AGUARDANDO_TEF,
            StatusPagamento.AGUARDANDO_PIX);

    private final PagamentoJpaRepository jpaRepository;
    private final PagamentoMapper mapper;

    @Override
    public Pagamento salvar(Pagamento pagamento) {
        return mapper.paraDomain(jpaRepository.save(mapper.paraEntity(pagamento)));
    }

    @Override
    public Pagamento salvarImediato(Pagamento pagamento) {
        return mapper.paraDomain(jpaRepository.saveAndFlush(mapper.paraEntity(pagamento)));
    }

    @Override
    public Optional<Pagamento> buscarPorCorrelationId(String correlationId) {
        return jpaRepository.findByCorrelationId(correlationId)
                .map(mapper::paraDomain);
    }

    @Override
    public Optional<Pagamento> buscarPorTxidPix(String txid) {
        return jpaRepository.findByPixTxid(txid)
                .map(mapper::paraDomain);
    }

    @Override
    public List<Pagamento> buscarPendentesAnteriores(LocalDateTime dataLimite) {
        return jpaRepository.findByStatusInAndIniciadoEmBefore(STATUS_PENDENTES, dataLimite)
                .stream()
                .map(mapper::paraDomain)
                .toList();
    }

    @Override
    public List<Pagamento> buscarAguardandoPix() {
        return jpaRepository.findByStatus(StatusPagamento.AGUARDANDO_PIX)
                .stream()
                .map(mapper::paraDomain)
                .toList();
    }

    @Override
    public Optional<Pagamento> buscarAguardandoPixPorPedidoId(String pedidoId) {
        return jpaRepository.findFirstByPedidoIdAndStatusOrderByIniciadoEmDesc(
                pedidoId, StatusPagamento.AGUARDANDO_PIX)
                .map(mapper::paraDomain);
    }
}
