package com.snackbar.pedidos.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.pedidos.application.ports.ContaMesaRepositoryPort;
import com.snackbar.pedidos.domain.entities.ContaMesa;
import com.snackbar.pedidos.domain.entities.StatusContaMesa;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ContaMesaRepositoryAdapter implements ContaMesaRepositoryPort {

    private final ContaMesaJpaRepository jpaRepository;

    @Override
    @Transactional
    public ContaMesa salvar(ContaMesa conta) {
        ContaMesaEntity entity = toEntity(conta);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContaMesa> buscarPorId(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContaMesa> buscarAbertaPorMesaECliente(String mesaId, String clienteId) {
        return jpaRepository.findByMesaClienteStatus(mesaId, clienteId, StatusContaMesa.ABERTA)
                .map(this::toDomain);
    }

    private ContaMesaEntity toEntity(ContaMesa conta) {
        ContaMesaEntity entity = ContaMesaEntity.builder()
                .id(conta.getId())
                .mesaId(conta.getMesaId())
                .numeroMesa(conta.getNumeroMesa())
                .clienteId(conta.getClienteId())
                .status(conta.getStatus())
                .valorCentavos(conta.getValorCentavos())
                .correlationId(conta.getCorrelationId())
                .createdAt(conta.getCreatedAt())
                .updatedAt(conta.getUpdatedAt())
                .build();
        conta.getPedidoIds().forEach(pedidoId ->
                entity.getPedidos().add(new ContaMesaPedidoEntity(conta.getId(), pedidoId)));
        return entity;
    }

    private ContaMesa toDomain(ContaMesaEntity entity) {
        List<String> pedidoIds = entity.getPedidos().stream()
                .map(ContaMesaPedidoEntity::getPedidoId)
                .collect(Collectors.toList());
        return ContaMesa.restaurar(
                entity.getId(),
                entity.getMesaId(),
                entity.getNumeroMesa(),
                entity.getClienteId(),
                entity.getStatus(),
                entity.getValorCentavos(),
                entity.getCorrelationId(),
                pedidoIds,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
