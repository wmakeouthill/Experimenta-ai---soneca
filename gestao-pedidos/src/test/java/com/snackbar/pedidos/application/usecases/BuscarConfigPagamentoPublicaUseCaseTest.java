package com.snackbar.pedidos.application.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;
import com.snackbar.pedidos.application.ports.ConfiguracaoPagamentoRepositoryPort;
import com.snackbar.pedidos.domain.entities.GatewayPagamento;
import com.snackbar.pedidos.domain.entities.ModoPagamentoMesa;
import com.snackbar.pedidos.infrastructure.config.PagamentoProperties;

@ExtendWith(MockitoExtension.class)
class BuscarConfigPagamentoPublicaUseCaseTest {

    @Mock
    private ConfiguracaoPagamentoRepositoryPort configuracaoRepository;

    private final PagamentoProperties properties = new PagamentoProperties();

    private BuscarConfigPagamentoPublicaUseCase useCase;

    private void criarUseCase() {
        useCase = new BuscarConfigPagamentoPublicaUseCase(configuracaoRepository, properties);
    }

    @Test
    void deveRetornarTudoDesativadoQuandoMasterSwitchDesligado() {
        properties.setEnabled(false);
        criarUseCase();

        var config = useCase.executar();

        assertFalse(config.pagamentosAtivos());
        assertFalse(config.totem().pixAtivo());
        assertFalse(config.mesa().pixAtivo());
        verify(configuracaoRepository, never()).buscar();
    }

    @Test
    void deveCombinarFlagsDoBancoQuandoHabilitado() {
        properties.setEnabled(true);
        properties.getGateway().setPix(GatewayPagamento.GETNET);
        when(configuracaoRepository.buscar()).thenReturn(new ConfiguracaoPagamentoDTO(
                true, false, true, false, ModoPagamentoMesa.POS_PAGO));
        criarUseCase();

        var config = useCase.executar();

        assertTrue(config.pagamentosAtivos());
        assertTrue(config.totem().pixAtivo());
        assertFalse(config.totem().cartaoAtivo());
        assertTrue(config.mesa().pixAtivo());
        assertEquals(ModoPagamentoMesa.POS_PAGO, config.mesa().modo());
        assertFalse(config.gatewayPixSimulado());
    }

    @Test
    void deveIndicarGatewaySimuladoQuandoPixSimulado() {
        properties.setEnabled(true);
        properties.getGateway().setPix(GatewayPagamento.SIMULADO);
        when(configuracaoRepository.buscar()).thenReturn(new ConfiguracaoPagamentoDTO(
                true, true, false, false, ModoPagamentoMesa.PRE_PAGO));
        criarUseCase();

        assertTrue(useCase.executar().gatewayPixSimulado());
    }
}
