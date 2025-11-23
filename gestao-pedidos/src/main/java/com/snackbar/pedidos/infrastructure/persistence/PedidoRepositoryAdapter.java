package com.snackbar.pedidos.infrastructure.persistence;

import com.snackbar.pedidos.application.ports.PedidoRepositoryPort;
import com.snackbar.pedidos.domain.entities.Pedido;
import com.snackbar.pedidos.domain.entities.StatusPedido;
import com.snackbar.pedidos.infrastructure.mappers.PedidoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PedidoRepositoryAdapter implements PedidoRepositoryPort {
    
    private final PedidoJpaRepository jpaRepository;
    private final PedidoMapper mapper;
    
    @Override
    public Pedido salvar(Pedido pedido) {
        PedidoEntity entity = mapper.paraEntity(pedido);
        PedidoEntity salvo = jpaRepository.save(entity);
        return mapper.paraDomain(salvo);
    }
    
    @Override
    public Optional<Pedido> buscarPorId(String id) {
        return jpaRepository.findById(id)
            .map(mapper::paraDomain);
    }
    
    @Override
    public List<Pedido> buscarTodos() {
        return jpaRepository.findAll().stream()
            .map(mapper::paraDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Pedido> buscarPorStatus(StatusPedido status) {
        return jpaRepository.findByStatus(status).stream()
            .map(mapper::paraDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Pedido> buscarPorClienteId(String clienteId) {
        return jpaRepository.findByClienteId(clienteId).stream()
            .map(mapper::paraDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Pedido> buscarPorDataPedido(LocalDateTime dataInicio, LocalDateTime dataFim) {
        return jpaRepository.findByDataPedidoBetween(dataInicio, dataFim).stream()
            .map(mapper::paraDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Pedido> buscarPorStatusEData(StatusPedido status, LocalDateTime dataInicio, LocalDateTime dataFim) {
        return jpaRepository.findByStatusAndDataPedidoBetween(status, dataInicio, dataFim).stream()
            .map(mapper::paraDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Pedido> buscarPorSessaoId(String sessaoId) {
        return jpaRepository.findBySessaoId(sessaoId).stream()
            .map(mapper::paraDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public int buscarUltimoNumeroPedido() {
        return jpaRepository.findMaxNumeroPedido()
            .orElse(0);
    }
}

