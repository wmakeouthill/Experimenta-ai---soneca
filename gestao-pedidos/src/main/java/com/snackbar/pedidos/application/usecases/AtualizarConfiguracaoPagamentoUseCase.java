package com.snackbar.pedidos.application.usecases;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;
import com.snackbar.pedidos.application.ports.ConfiguracaoPagamentoRepositoryPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AtualizarConfiguracaoPagamentoUseCase {

    private final ConfiguracaoPagamentoRepositoryPort configuracaoRepository;

    @Transactional
    public ConfiguracaoPagamentoDTO executar(ConfiguracaoPagamentoDTO config) {
        var salva = configuracaoRepository.salvar(config);
        log.info("Configuracao de pagamento atualizada: pixTotem={} cartaoTotem={} pixMesa={} cartaoMesa={} modoMesa={}",
                salva.pixTotemAtivo(), salva.cartaoTotemAtivo(),
                salva.pixMesaAtivo(), salva.cartaoMesaAtivo(), salva.modoMesa());
        return salva;
    }
}
