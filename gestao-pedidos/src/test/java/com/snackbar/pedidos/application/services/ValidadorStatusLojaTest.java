package com.snackbar.pedidos.application.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.kernel.domain.exceptions.BusinessRuleException;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import com.snackbar.pedidos.domain.entities.SessaoTrabalho;

@ExtendWith(MockitoExtension.class)
class ValidadorStatusLojaTest {

    @Mock
    private SessaoTrabalhoRepositoryPort sessaoTrabalhoRepository;

    @InjectMocks
    private ValidadorStatusLoja validador;

    private SessaoTrabalho sessao(boolean pausada) {
        SessaoTrabalho sessao = SessaoTrabalho.criar(1, "usuario-1", BigDecimal.ZERO);
        if (pausada) {
            sessao.pausar();
        }
        return sessao;
    }

    @Test
    void lojaFechadaRecusaTodosOsCanais() {
        when(sessaoTrabalhoRepository.buscarSessaoAtiva()).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, validador::exigirSessaoAtiva);
        assertThrows(BusinessRuleException.class, validador::exigirLojaAberta);
    }

    @Test
    void lojaPausadaAceitaBalcaoERecusaMesaETotem() {
        when(sessaoTrabalhoRepository.buscarSessaoAtiva()).thenReturn(Optional.of(sessao(true)));

        assertDoesNotThrow(validador::exigirSessaoAtiva);
        assertThrows(BusinessRuleException.class, validador::exigirLojaAberta);
    }

    @Test
    void lojaAbertaAceitaTodosOsCanais() {
        when(sessaoTrabalhoRepository.buscarSessaoAtiva()).thenReturn(Optional.of(sessao(false)));

        assertDoesNotThrow(validador::exigirSessaoAtiva);
        assertDoesNotThrow(validador::exigirLojaAberta);
    }
}
