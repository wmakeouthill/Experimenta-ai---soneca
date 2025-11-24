package com.snackbar.pedidos.infrastructure.mappers;

import com.snackbar.pedidos.domain.entities.SessaoTrabalho;
import com.snackbar.pedidos.infrastructure.persistence.SessaoTrabalhoEntity;
import org.springframework.stereotype.Component;

@Component
public class SessaoTrabalhoMapper {

    public SessaoTrabalhoEntity paraEntity(SessaoTrabalho sessao) {
        return SessaoTrabalhoEntity.builder()
                .id(sessao.getId())
                .numeroSessao(sessao.getNumeroSessao())
                .dataInicio(sessao.getDataInicio())
                .dataInicioCompleta(sessao.getDataInicioCompleta())
                .dataFim(sessao.getDataFim())
                .status(sessao.getStatus())
                .usuarioId(sessao.getUsuarioId())
                .createdAt(sessao.getCreatedAt())
                .updatedAt(sessao.getUpdatedAt())
                .build();
    }

    public SessaoTrabalho paraDomain(SessaoTrabalhoEntity entity) {
        SessaoTrabalho sessao = SessaoTrabalho.criar(
                entity.getNumeroSessao(),
                entity.getUsuarioId());

        sessao.restaurarDoBanco(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());

        // IMPORTANTE: Restaurar as datas de início do banco (imutáveis após criação)
        sessao.restaurarDatasInicioDoBanco(
                entity.getDataInicio(),
                entity.getDataInicioCompleta());

        sessao.restaurarStatusDoBanco(entity.getStatus(), entity.getDataFim());

        return sessao;
    }
}
