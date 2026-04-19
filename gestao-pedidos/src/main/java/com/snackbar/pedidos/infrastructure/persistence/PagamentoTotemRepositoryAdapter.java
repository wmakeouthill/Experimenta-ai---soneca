package com.snackbar.pedidos.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.snackbar.pedidos.application.ports.PagamentoTotemRepositoryPort;
import com.snackbar.pedidos.domain.entities.PagamentoTotem;
import com.snackbar.pedidos.domain.entities.StatusPagamentoTotem;
import com.snackbar.pedidos.infrastructure.mappers.PagamentoTotemMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PagamentoTotemRepositoryAdapter implements PagamentoTotemRepositoryPort {

    private static final List<StatusPagamentoTotem> STATUS_PENDENTES = List.of(
            StatusPagamentoTotem.INICIADO,
            StatusPagamentoTotem.AGUARDANDO_TEF,
            StatusPagamentoTotem.AGUARDANDO_PIX);

    private final PagamentoTotemJpaRepository jpaRepository;
    private final PagamentoTotemMapper mapper;

    @Override
    public PagamentoTotem salvar(PagamentoTotem pagamento) {
        return mapper.paraDomain(jpaRepository.save(mapper.paraEntity(pagamento)));
    }

    @Override
    public Optional<PagamentoTotem> buscarPorCorrelationId(String correlationId) {
        return jpaRepository.findByCorrelationId(correlationId)
                .map(mapper::paraDomain);
    }

    @Override
    public Optional<PagamentoTotem> buscarPorTxidPix(String txid) {
        return jpaRepository.findByPixTxid(txid)
                .map(mapper::paraDomain);
    }

    @Override
    public List<PagamentoTotem> buscarPendentesAnteriores(LocalDateTime dataLimite) {
        return jpaRepository.findByStatusInAndIniciadoEmBefore(STATUS_PENDENTES, dataLimite)
                .stream()
                .map(mapper::paraDomain)
                .toList();
    }
}
